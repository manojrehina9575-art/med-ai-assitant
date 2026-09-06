import { Component, type ErrorInfo, type ReactNode } from 'react';

interface AnatomyErrorBoundaryProps {
  children: ReactNode;
  fallback: ReactNode;
  /** Resets the boundary when the selection changes, so one failure is not permanent. */
  resetKey?: string;
}

interface AnatomyErrorBoundaryState {
  failed: boolean;
}

/**
 * Keeps a 3D failure local.
 *
 * A missing or corrupt model, a WebGL context loss or a throw inside the lazily loaded scene must
 * never take down the Clinical Workspace or hide the report's anatomy metadata.
 */
export class AnatomyErrorBoundary extends Component<AnatomyErrorBoundaryProps, AnatomyErrorBoundaryState> {
  state: AnatomyErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): AnatomyErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Diagnostics only; nothing user-facing carries the stack.
    console.error('Anatomy viewer failed to render', error, info.componentStack);
  }

  componentDidUpdate(previous: AnatomyErrorBoundaryProps): void {
    if (this.state.failed && previous.resetKey !== this.props.resetKey) {
      this.setState({ failed: false });
    }
  }

  render(): ReactNode {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}
