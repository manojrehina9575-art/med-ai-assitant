import * as React from 'react';
import * as LabelPrimitive from '@radix-ui/react-label';
import { cn } from '@/utils/cn';

const Label = React.forwardRef<
  React.ElementRef<typeof LabelPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof LabelPrimitive.Root>
>(({ className, ...props }, ref) => (
  <LabelPrimitive.Root
    ref={ref}
    className={cn(
      'block text-xs font-semibold leading-none mb-1.5 select-none',
      className
    )}
    style={{ color: 'var(--clr-text-2, #94a3b8)' }}
    {...props}
  />
));
Label.displayName = LabelPrimitive.Root.displayName;

export { Label };
