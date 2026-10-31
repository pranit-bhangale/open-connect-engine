import type { RoomResponse, ScrollResponse, RawMessageResponse, ChatMessage } from './types';

export const CHAT_API_BASE =
  import.meta.env.CHAT_API_URL !== undefined
    ? import.meta.env.CHAT_API_URL
    : (import.meta.env.VITE_CHAT_API_URL !== undefined
        ? import.meta.env.VITE_CHAT_API_URL
        : '');

export const CHAT_WS_URL =
  import.meta.env.CHAT_WS_URL ||
  import.meta.env.VITE_CHAT_WS_URL ||
  (CHAT_API_BASE
    ? (CHAT_API_BASE.startsWith('https://')
        ? CHAT_API_BASE.replace('https://', 'wss://') + '/chat/websocket'
        : CHAT_API_BASE.replace('http://', 'ws://') + '/chat/websocket')
    : (typeof window !== 'undefined'
        ? `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/chat/websocket`
        : 'ws://localhost:8080/chat/websocket'));

export class ServiceUnavailableError extends Error {
  constructor(message: string = 'Chat service is currently unavailable or unreachable.') {
    super(message);
    this.name = 'ServiceUnavailableError';
  }
}

/**
 * Checks whether the backend engine is running and reachable.
 */
export async function checkBackendHealth(): Promise<{ isOnline: boolean; message?: string }> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    // 1. Try dedicated lightweight liveness probe first
    let res: Response | null = null;
    try {
      res = await fetch(`${CHAT_API_BASE}/actuator/health/liveness`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        signal: controller.signal,
      });
    } catch {
      // fallback
    }

    // 2. If liveness returned 404 or wasn't available, fall back to /actuator/health
    if (!res || (!res.ok && res.status !== 503)) {
      try {
        res = await fetch(`${CHAT_API_BASE}/actuator/health`, {
          method: 'GET',
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        });
      } catch {
        // network failure
      }
    }

    clearTimeout(timeoutId);

    if (res && (res.ok || res.status === 200 || res.status === 503)) {
      return { isOnline: true };
    }
    return {
      isOnline: false,
      message: res ? `Backend returned HTTP ${res.status}` : 'Backend is offline or unreachable',
    };
  } catch (err: unknown) {
    let msg = 'Backend is offline or unreachable';
    if (err instanceof Error) {
      if (err.name === 'AbortError') {
        msg = 'Connection to backend timed out';
      } else {
        msg = err.message;
      }
    }
    return { isOnline: false, message: msg };
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    if (res.status === 503 || res.status === 502 || res.status === 504) {
      throw new ServiceUnavailableError('Chat service is currently unavailable (HTTP ' + res.status + ').');
    }

    let errorMsg = `Server error (${res.status})`;
    try {
      const errorData = await res.json();
      if (errorData.message) {
        errorMsg = errorData.message;
      }
      if (errorData.errors && typeof errorData.errors === 'object') {
        const fieldErrors = Object.values(errorData.errors).join(', ');
        if (fieldErrors) errorMsg = `${errorMsg}: ${fieldErrors}`;
      }
    } catch {
      // ignore
    }
    throw new Error(errorMsg);
  }
  return res.json() as Promise<T>;
}

async function safeFetch(url: string, options: RequestInit): Promise<Response> {
  try {
    return await fetch(url, options);
  } catch (err: unknown) {
    if (err instanceof TypeError || (err instanceof Error && err.message.includes('Failed to fetch'))) {
      throw new ServiceUnavailableError(
        'Cannot reach chat backend. The service appears to be offline or blocked.'
      );
    }
    throw err;
  }
}

export async function createRoomApi(
  username: string,
  roomName: string,
  description: string
): Promise<RoomResponse> {
  const res = await safeFetch(`${CHAT_API_BASE}/api/rooms/open`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Version': 'v1',
    },
    body: JSON.stringify({
      user: { username: username.trim() },
      room: { roomName: roomName.trim(), description: description.trim() || 'Open discussion' },
    }),
  });
  return handleResponse<RoomResponse>(res);
}

export async function joinRoomApi(
  username: string,
  roomCode: string
): Promise<RoomResponse> {
  const res = await safeFetch(`${CHAT_API_BASE}/api/rooms/join`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Version': 'v1',
    },
    body: JSON.stringify({
      user: { username: username.trim() },
      detail: { roomCode: roomCode.trim() },
    }),
  });
  return handleResponse<RoomResponse>(res);
}

export async function getRoomDetailApi(roomId: string): Promise<RoomResponse> {
  const res = await safeFetch(`${CHAT_API_BASE}/api/rooms/${roomId}`, {
    method: 'GET',
    headers: {
      'X-API-Version': 'v1',
    },
  });
  return handleResponse<RoomResponse>(res);
}

export async function fetchMessagesApi(
  roomId: string,
  pageSize: number = 30,
  scrollId?: string | null,
  userName?: string | null
): Promise<ScrollResponse<ChatMessage>> {
  const params = new URLSearchParams({
    pageSize: String(pageSize),
    sortBy: 'createdAt',
    sortDirection: 'ASC',
    scrollDirection: 'forward',
  });
  if (scrollId) {
    params.set('scrollId', scrollId);
  }
  if (userName && userName.trim()) {
    params.set('userName', userName.trim());
  }

  const headers: Record<string, string> = {
    'X-API-Version': 'v1',
  };
  if (userName && userName.trim()) {
    headers['X-User-Name'] = userName.trim();
  }

  const res = await safeFetch(`${CHAT_API_BASE}/api/rooms/${roomId}/messages?${params.toString()}`, {
    method: 'GET',
    headers,
  });

  const data = await handleResponse<ScrollResponse<RawMessageResponse>>(res);

  // Map raw backend message DTO to frontend ChatMessage
  const mappedContents: ChatMessage[] = data.contents.map((m) => ({
    id: m.id,
    roomId: m.roomDetail.id,
    senderId: m.userDetail.id,
    senderName: m.userDetail.username,
    content: m.content.message,
    type: m.content.messageType,
    sentAt: m.sentAt,
  }));

  return {
    ...data,
    contents: mappedContents,
  };
}

export async function closeRoomApi(
  roomId: string,
  username: string
): Promise<RoomResponse> {
  const res = await safeFetch(`${CHAT_API_BASE}/api/rooms/${roomId}/close`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Version': 'v1',
    },
    body: JSON.stringify({
      username: username.trim(),
    }),
  });
  return handleResponse<RoomResponse>(res);
}

export async function leaveRoomApi(
  roomId: string,
  username: string
): Promise<RoomResponse> {
  const res = await safeFetch(`${CHAT_API_BASE}/api/rooms/${roomId}/leave`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'X-API-Version': 'v1',
    },
    body: JSON.stringify({
      username: username.trim(),
    }),
  });
  return handleResponse<RoomResponse>(res);
}
