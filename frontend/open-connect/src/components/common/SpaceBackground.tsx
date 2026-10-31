import React, { useEffect, useRef } from 'react';

interface Star {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  baseAlpha: number;
  pulsePhase: number;
  pulseSpeed: number;
  color: string;
}

interface ShootingStar {
  x: number;
  y: number;
  length: number;
  speed: number;
  angle: number;
  opacity: number;
  active: boolean;
}

const STAR_COLORS = [
  '#ffffff',
  '#93c5fd', // Celestial light blue
  '#c4b5fd', // Soft nebula purple
  '#67e8f9', // Cyan
  '#e0e7ff', // Indigo white
];

export const SpaceBackground: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    // Responsive star count based on screen area
    const starCount = Math.min(220, Math.max(90, Math.floor((width * height) / 9000)));
    const stars: Star[] = [];

    for (let i = 0; i < starCount; i++) {
      stars.push({
        x: Math.random() * width,
        y: Math.random() * height,
        vx: (Math.random() - 0.5) * 0.35,
        vy: (Math.random() - 0.5) * 0.35,
        radius: Math.random() < 0.8 ? Math.random() * 1.4 + 0.5 : Math.random() * 1.8 + 1.2,
        baseAlpha: Math.random() * 0.7 + 0.3,
        pulsePhase: Math.random() * Math.PI * 2,
        pulseSpeed: Math.random() * 0.02 + 0.008,
        color: STAR_COLORS[Math.floor(Math.random() * STAR_COLORS.length)],
      });
    }

    // Shooting stars system
    let shootingStar: ShootingStar | null = null;
    let nextShootingStarTime = Date.now() + Math.random() * 3000 + 2000;

    const spawnShootingStar = () => {
      const angle = (Math.PI / 4) + (Math.random() - 0.5) * 0.2; // ~45 degrees diagonal
      shootingStar = {
        x: Math.random() * width * 0.8,
        y: Math.random() * height * 0.4,
        length: Math.random() * 80 + 70,
        speed: Math.random() * 8 + 12,
        angle,
        opacity: 1,
        active: true,
      };
      nextShootingStarTime = Date.now() + Math.random() * 6000 + 4000;
    };

    // Handle mouse movement for subtle gravitational reaction
    let mouseX = width / 2;
    let mouseY = height / 2;
    let isMouseOver = false;

    const handleMouseMove = (e: MouseEvent) => {
      mouseX = e.clientX;
      mouseY = e.clientY;
      isMouseOver = true;
    };

    const handleMouseLeave = () => {
      isMouseOver = false;
    };

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };

    window.addEventListener('resize', handleResize);
    window.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseleave', handleMouseLeave);

    const render = () => {
      ctx.clearRect(0, 0, width, height);

      // Deep space backdrop with dynamic radial nebula glow
      const bgGradient = ctx.createRadialGradient(
        width * 0.5,
        height * 0.45,
        50,
        width * 0.5,
        height * 0.5,
        Math.max(width, height) * 0.85
      );
      bgGradient.addColorStop(0, '#0a0a14');
      bgGradient.addColorStop(0.5, '#07070e');
      bgGradient.addColorStop(1, '#040408');

      ctx.fillStyle = bgGradient;
      ctx.fillRect(0, 0, width, height);

      // Draw faint constellation lines between nearby stars
      const maxConnectDistance = 85;
      for (let i = 0; i < stars.length; i++) {
        for (let j = i + 1; j < stars.length; j++) {
          const dx = stars[i].x - stars[j].x;
          const dy = stars[i].y - stars[j].y;
          const distSq = dx * dx + dy * dy;

          if (distSq < maxConnectDistance * maxConnectDistance) {
            const dist = Math.sqrt(distSq);
            const lineAlpha = (1 - dist / maxConnectDistance) * 0.12;
            ctx.beginPath();
            ctx.strokeStyle = `rgba(147, 197, 253, ${lineAlpha})`;
            ctx.lineWidth = 0.6;
            ctx.moveTo(stars[i].x, stars[i].y);
            ctx.lineTo(stars[j].x, stars[j].y);
            ctx.stroke();
          }
        }
      }

      // Draw and update stars
      const now = Date.now();
      for (let i = 0; i < stars.length; i++) {
        const star = stars[i];

        // Twinkle calculation
        star.pulsePhase += star.pulseSpeed;
        const currentAlpha = Math.max(
          0.1,
          Math.min(1, star.baseAlpha + Math.sin(star.pulsePhase) * 0.3)
        );

        // Position update
        star.x += star.vx;
        star.y += star.vy;

        // Subtle interactive mouse reaction
        if (isMouseOver) {
          const mdx = star.x - mouseX;
          const mdy = star.y - mouseY;
          const mDist = Math.sqrt(mdx * mdx + mdy * mdy);
          if (mDist < 120 && mDist > 0) {
            const force = ((120 - mDist) / 120) * 0.4;
            star.x += (mdx / mDist) * force;
            star.y += (mdy / mDist) * force;
          }
        }

        // Seamless wrap around edges
        if (star.x < 0) star.x = width;
        else if (star.x > width) star.x = 0;
        if (star.y < 0) star.y = height;
        else if (star.y > height) star.y = 0;

        // Render star point
        ctx.beginPath();
        ctx.arc(star.x, star.y, star.radius, 0, Math.PI * 2);
        ctx.fillStyle = star.color;
        ctx.globalAlpha = currentAlpha;
        ctx.fill();

        // Extra subtle halo for larger glowing stars
        if (star.radius > 1.6) {
          ctx.beginPath();
          ctx.arc(star.x, star.y, star.radius * 2.8, 0, Math.PI * 2);
          ctx.fillStyle = star.color;
          ctx.globalAlpha = currentAlpha * 0.15;
          ctx.fill();
        }
      }

      // Check shooting star trigger
      if (now > nextShootingStarTime && (!shootingStar || !shootingStar.active)) {
        spawnShootingStar();
      }

      // Render shooting star if active
      if (shootingStar && shootingStar.active) {
        const cos = Math.cos(shootingStar.angle);
        const sin = Math.sin(shootingStar.angle);

        const tailX = shootingStar.x - cos * shootingStar.length;
        const tailY = shootingStar.y - sin * shootingStar.length;

        const grad = ctx.createLinearGradient(
          tailX,
          tailY,
          shootingStar.x,
          shootingStar.y
        );
        grad.addColorStop(0, 'rgba(255, 255, 255, 0)');
        grad.addColorStop(0.7, `rgba(165, 180, 252, ${shootingStar.opacity * 0.4})`);
        grad.addColorStop(1, `rgba(255, 255, 255, ${shootingStar.opacity})`);

        ctx.beginPath();
        ctx.strokeStyle = grad;
        ctx.lineWidth = 1.8;
        ctx.moveTo(tailX, tailY);
        ctx.lineTo(shootingStar.x, shootingStar.y);
        ctx.stroke();

        // Advance shooting star
        shootingStar.x += cos * shootingStar.speed;
        shootingStar.y += sin * shootingStar.speed;
        shootingStar.opacity -= 0.018;

        if (
          shootingStar.opacity <= 0 ||
          shootingStar.x > width + 100 ||
          shootingStar.y > height + 100
        ) {
          shootingStar.active = false;
        }
      }

      ctx.globalAlpha = 1;
      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseleave', handleMouseLeave);
    };
  }, []);

  return (
    <div className="fixed inset-0 pointer-events-none -z-10 overflow-hidden">
      {/* HTML5 Canvas for live moving dots, stars, constellations and shooting stars */}
      <canvas
        ref={canvasRef}
        className="absolute inset-0 w-full h-full block"
      />

      {/* Layered cosmic nebula atmospheric glows */}
      <div className="absolute top-[-10%] left-[20%] w-[600px] h-[600px] rounded-full bg-indigo-600/[0.07] blur-[150px] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[15%] w-[650px] h-[650px] rounded-full bg-purple-600/[0.06] blur-[160px] pointer-events-none" />
      <div className="absolute top-[40%] right-[30%] w-[500px] h-[500px] rounded-full bg-sky-500/[0.04] blur-[140px] pointer-events-none" />
    </div>
  );
};

export default SpaceBackground;
