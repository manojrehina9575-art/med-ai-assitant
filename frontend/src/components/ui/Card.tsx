import * as React from 'react';
import { cn } from '@/utils/cn';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  hover?: boolean;
}

function Card({ className, hover = false, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-xl border',
        hover && 'transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg cursor-pointer',
        className
      )}
      style={{
        background: 'var(--surface, #111827)',
        borderColor: 'var(--clr-border, #1e2d45)',
      }}
      {...props}
    />
  );
}

function CardHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col space-y-1 p-5 pb-3', className)} {...props} />;
}

function CardTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={cn('text-sm font-semibold leading-none tracking-tight text-white', className)}
      style={{ fontFamily: 'Plus Jakarta Sans, Inter, sans-serif' }}
      {...props}
    />
  );
}

function CardDescription({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p
      className={cn('text-xs', className)}
      style={{ color: 'var(--clr-text-3, #64748b)' }}
      {...props}
    />
  );
}

function CardContent({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-5 pt-0', className)} {...props} />;
}

function CardFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('flex items-center p-5 pt-3', className)}
      style={{ borderTop: '1px solid var(--clr-border, #1e2d45)' }}
      {...props}
    />
  );
}

export { Card, CardHeader, CardFooter, CardTitle, CardDescription, CardContent };
