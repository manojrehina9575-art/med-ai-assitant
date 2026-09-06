/**
 * Viewer view-state, kept as pure data so reset/isolate behaviour is unit-testable without WebGL.
 *
 * This is presentation state only. It never touches the clinical `AnatomySelection`: resetting the
 * camera must not clear which finding the reviewer is looking at.
 */
export interface AnatomyViewState {
  /** Selected structure fully visible, rest strongly faded. */
  isolated: boolean;
  /** Incremented to ask the scene for a camera reset to the whole-body default. */
  resetNonce: number;
  /** Incremented to ask the scene to zoom the camera to the selected structure. */
  focusNonce: number;
}

export type AnatomyViewAction =
  | { type: 'RESET_VIEW' }
  | { type: 'ISOLATE_STRUCTURE' }
  | { type: 'SHOW_FULL_SKELETON' }
  | { type: 'SELECTION_CHANGED' };

export const initialAnatomyViewState: AnatomyViewState = {
  isolated: false,
  resetNonce: 0,
  focusNonce: 0,
};

export function reduceAnatomyViewState(
  state: AnatomyViewState,
  action: AnatomyViewAction,
  options: { canIsolate: boolean }
): AnatomyViewState {
  switch (action.type) {
    case 'RESET_VIEW':
      // Restores the default whole-body camera and full skeleton visibility.
      return { ...state, isolated: false, resetNonce: state.resetNonce + 1 };
    case 'ISOLATE_STRUCTURE':
      // "Focus on Structure": fades everything else and zooms the camera to it.
      return options.canIsolate ? { ...state, isolated: true, focusNonce: state.focusNonce + 1 } : state;
    case 'SHOW_FULL_SKELETON':
      return { ...state, isolated: false };
    case 'SELECTION_CHANGED':
      // A selection the viewer cannot render must not leave the skeleton hidden.
      return options.canIsolate ? state : { ...state, isolated: false };
  }
}
