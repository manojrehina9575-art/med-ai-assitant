import * as React from 'react';
import { cn } from '@/utils/cn';

export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'prefix'> {
  prefix?: React.ReactNode;
  suffix?: React.ReactNode;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, prefix, suffix, ...props }, ref) => {
    if (prefix || suffix) {
      return (
        <div
          className="flex items-center h-10 rounded-lg overflow-hidden"
          style={{
            background: 'var(--surface-2, #1a2235)',
            border: '1px solid var(--clr-border, #1e2d45)',
            transition: 'border-color 150ms ease, box-shadow 150ms ease',
          }}
          onFocusCapture={(e) => {
            (e.currentTarget as HTMLDivElement).style.borderColor = '#3b82f6';
            (e.currentTarget as HTMLDivElement).style.boxShadow = '0 0 0 3px rgba(59,130,246,0.15)';
          }}
          onBlurCapture={(e) => {
            (e.currentTarget as HTMLDivElement).style.borderColor = 'var(--clr-border, #1e2d45)';
            (e.currentTarget as HTMLDivElement).style.boxShadow = 'none';
          }}
        >
          {prefix && (
            <div className="flex items-center justify-center px-3 h-full" style={{ color: 'var(--clr-text-3, #64748b)' }}>
              {prefix}
            </div>
          )}
          <input
            type={type}
            className={cn(
              'flex-1 h-full bg-transparent px-3 text-sm outline-none',
              prefix && 'pl-0',
              suffix && 'pr-0',
              className
            )}
            style={{ color: 'var(--clr-text, #f1f5f9)' }}
            ref={ref}
            {...props}
          />
          {suffix && (
            <div className="flex items-center justify-center px-3 h-full" style={{ color: 'var(--clr-text-3, #64748b)' }}>
              {suffix}
            </div>
          )}
        </div>
      );
    }

    return (
      <input
        type={type}
        className={cn('input-field', className)}
        ref={ref}
        {...props}
      />
    );
  }
);
Input.displayName = 'Input';

export { Input };
