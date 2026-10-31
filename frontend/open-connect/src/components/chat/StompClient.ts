/**
 * Lightweight, zero-dependency STOMP 1.2 client for WebSocket connections.
 * Provides resilient auto-reconnect, subscription management, and type safety.
 */

export interface StompHeaders {
  [key: string]: string;
}

export interface StompFrame {
  command: string;
  headers: StompHeaders;
  body: string;
}

export interface StompSubscription {
  id: string;
  destination: string;
  callback: (body: string, headers: StompHeaders) => void;
  unsubscribe: () => void;
}

export class StompClient {
  private url: string;
  private ws: WebSocket | null = null;
  private connected: boolean = false;
  private connecting: boolean = false;
  private subCounter: number = 0;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private onConnectCallbacks: Array<() => void> = [];
  private onDisconnectCallbacks: Array<() => void> = [];
  private onErrorCallbacks: Array<(error: Error | string) => void> = [];
  private reconnectTimer: number | null = null;
  private shouldReconnect: boolean = true;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 10;
  private buffer: string = '';

  constructor(url: string) {
    this.url = url;
  }

  public connect(): void {
    if (this.connected || this.connecting) return;
    this.connecting = true;
    this.shouldReconnect = true;

    try {
      this.ws = new WebSocket(this.url);
    } catch (err) {
      this.connecting = false;
      this.handleError(err instanceof Error ? err : new Error(String(err)));
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.sendRaw('CONNECT\naccept-version:1.1,1.2\nheart-beat:10000,10000\n\n\0');
    };

    this.ws.onmessage = (event: MessageEvent) => {
      this.handleIncomingData(event.data);
    };

    this.ws.onerror = () => {
      this.handleError(new Error('WebSocket connection encountered an error'));
    };

    this.ws.onclose = () => {
      this.connected = false;
      this.connecting = false;
      this.onDisconnectCallbacks.forEach((cb) => {
        try {
          cb();
        } catch {
          // silent error handling
        }
      });
      if (this.shouldReconnect) {
        this.scheduleReconnect();
      }
    };
  }

  public isConnected(): boolean {
    return this.connected;
  }

  public subscribe(
    destination: string,
    callback: (body: string, headers: StompHeaders) => void
  ): StompSubscription {
    // If already subscribed to this destination, update callback and return existing sub to prevent duplicate frames
    for (const sub of this.subscriptions.values()) {
      if (sub.destination === destination) {
        sub.callback = callback;
        return sub;
      }
    }

    const id = `sub-${++this.subCounter}`;
    const sub: StompSubscription = {
      id,
      destination,
      callback,
      unsubscribe: () => this.unsubscribe(id),
    };

    this.subscriptions.set(id, sub);

    if (this.connected) {
      this.sendRaw(`SUBSCRIBE\nid:${id}\ndestination:${destination}\nack:auto\n\n\0`);
    }

    return sub;
  }

  public unsubscribe(id: string): void {
    const sub = this.subscriptions.get(id);
    if (!sub) return;
    this.subscriptions.delete(id);

    if (this.connected) {
      this.sendRaw(`UNSUBSCRIBE\nid:${id}\n\n\0`);
    }
  }

  public send(destination: string, body: unknown = {}, headers: StompHeaders = {}): void {
    if (!this.connected) {
      return;
    }

    const payload = typeof body === 'string' ? body : JSON.stringify(body);
    const byteLength = new TextEncoder().encode(payload).length;
    let headerStr = `SEND\ndestination:${destination}\ncontent-type:application/json;charset=UTF-8\ncontent-length:${byteLength}\n`;

    for (const [k, v] of Object.entries(headers)) {
      headerStr += `${k}:${v}\n`;
    }

    headerStr += `\n${payload}\0`;
    this.sendRaw(headerStr);
  }

  public onConnect(callback: () => void): () => void {
    this.onConnectCallbacks.push(callback);
    if (this.connected) {
      try {
        callback();
      } catch {
        // silent error handling
      }
    }
    return () => {
      this.onConnectCallbacks = this.onConnectCallbacks.filter((cb) => cb !== callback);
    };
  }

  public onDisconnect(callback: () => void): () => void {
    this.onDisconnectCallbacks.push(callback);
    return () => {
      this.onDisconnectCallbacks = this.onDisconnectCallbacks.filter((cb) => cb !== callback);
    };
  }

  public onError(callback: (error: Error | string) => void): () => void {
    this.onErrorCallbacks.push(callback);
    return () => {
      this.onErrorCallbacks = this.onErrorCallbacks.filter((cb) => cb !== callback);
    };
  }

  public disconnect(): void {
    this.shouldReconnect = false;
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    const socket = this.ws;
    this.ws = null;
    this.connected = false;
    this.connecting = false;
    this.buffer = '';
    this.subscriptions.clear();
    this.onConnectCallbacks = [];
    this.onDisconnectCallbacks = [];
    this.onErrorCallbacks = [];

    if (socket) {
      socket.onopen = null;
      socket.onmessage = null;
      socket.onerror = null;

      try {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send('DISCONNECT\n\n\0');
          setTimeout(() => {
            try {
              if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
                socket.close(1000, 'Normal closure');
              }
            } catch {
              // ignore
            }
          }, 150);
        } else {
          socket.close();
        }
      } catch {
        // silent closure
      }
    }
  }

  private sendRaw(data: string): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(data);
    }
  }

  private handleIncomingData(data: string): void {
    this.buffer += data;
    const frames = this.buffer.split('\0');
    // The last element is whatever remains after the last NULL byte
    this.buffer = frames.pop() || '';

    for (const rawFrame of frames) {
      const clean = rawFrame.trimStart();
      if (!clean) continue; // Heartbeat / newline
      const frame = this.parseFrame(clean);
      if (!frame) continue;

      if (frame.command === 'CONNECTED') {
        this.connected = true;
        this.connecting = false;
        this.reconnectAttempts = 0;

        // Resubscribe all active subscriptions
        for (const sub of this.subscriptions.values()) {
          this.sendRaw(`SUBSCRIBE\nid:${sub.id}\ndestination:${sub.destination}\nack:auto\n\n\0`);
        }

        this.onConnectCallbacks.forEach((cb) => {
          try {
            cb();
          } catch {
            // silent callback
          }
        });
      } else if (frame.command === 'MESSAGE') {
        const subId = frame.headers['subscription'];
        const sub = subId ? this.subscriptions.get(subId) : null;
        if (sub) {
          try {
            sub.callback(frame.body, frame.headers);
          } catch {
            // silent callback
          }
        }
      } else if (frame.command === 'ERROR') {
        this.handleError(new Error(frame.headers['message'] || frame.body || 'STOMP Protocol Error'));
      }
    }
  }

  private parseFrame(raw: string): StompFrame | null {
    const divider = raw.indexOf('\n\n');
    if (divider === -1) return null;

    const headerPart = raw.substring(0, divider);
    const body = raw.substring(divider + 2);
    const lines = headerPart.split('\n');
    const command = lines[0].trim();
    const headers: StompHeaders = {};

    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;
      const colonIdx = line.indexOf(':');
      if (colonIdx !== -1) {
        const key = line.substring(0, colonIdx).trim();
        const value = line.substring(colonIdx + 1).trim();
        if (!headers[key]) {
          headers[key] = value;
        }
      }
    }

    return { command, headers, body };
  }

  private handleError(error: Error | string): void {
    this.onErrorCallbacks.forEach((cb) => {
      try {
        cb(error);
      } catch {
        // silent error handler
      }
    });
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      return;
    }
    const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), 10000);
    this.reconnectAttempts++;
    this.reconnectTimer = window.setTimeout(() => {
      this.connect();
    }, delay);
  }
}
