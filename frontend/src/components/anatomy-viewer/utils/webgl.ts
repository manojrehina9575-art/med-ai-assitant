/**
 * Whether this environment can actually run the 3D viewer.
 *
 * Returns false in jsdom and in browsers with WebGL disabled, which lets the panel fall back to its
 * text metadata instead of mounting a renderer that would throw.
 */
export function supportsWebGl(): boolean {
  if (typeof document === 'undefined' || typeof window === 'undefined') return false;

  // Cheap capability check first: it short-circuits environments without WebGL at all (jsdom) so
  // no context is requested there.
  if (!('WebGL2RenderingContext' in window) && !('WebGLRenderingContext' in window)) {
    return false;
  }

  try {
    const canvas = document.createElement('canvas');
    return Boolean(
      canvas.getContext('webgl2') ||
        canvas.getContext('webgl') ||
        canvas.getContext('experimental-webgl')
    );
  } catch {
    return false;
  }
}
