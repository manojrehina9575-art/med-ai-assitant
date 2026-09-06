import { describe, expect, it } from 'vitest';
import {
  initialAnatomyViewState,
  reduceAnatomyViewState,
  type AnatomyViewState,
} from './anatomyViewerState';

const supported = { canIsolate: true };
const unsupported = { canIsolate: false };

describe('reduceAnatomyViewState', () => {
  it('starts with the full skeleton and no pending reset or focus', () => {
    expect(initialAnatomyViewState).toEqual({ isolated: false, resetNonce: 0, focusNonce: 0 });
  });

  it('isolates and requests a camera focus only when the selection is renderable', () => {
    expect(reduceAnatomyViewState(initialAnatomyViewState, { type: 'ISOLATE_STRUCTURE' }, supported))
      .toEqual({ isolated: true, resetNonce: 0, focusNonce: 1 });
    expect(reduceAnatomyViewState(initialAnatomyViewState, { type: 'ISOLATE_STRUCTURE' }, unsupported))
      .toBe(initialAnatomyViewState);
  });

  it('restores the full skeleton on Show Full Skeleton without moving the camera', () => {
    const isolated: AnatomyViewState = { isolated: true, resetNonce: 2, focusNonce: 3 };
    expect(reduceAnatomyViewState(isolated, { type: 'SHOW_FULL_SKELETON' }, supported))
      .toEqual({ isolated: false, resetNonce: 2, focusNonce: 3 });
  });

  it('reset clears isolation and requests a new whole-body camera frame', () => {
    const isolated: AnatomyViewState = { isolated: true, resetNonce: 4, focusNonce: 1 };
    expect(reduceAnatomyViewState(isolated, { type: 'RESET_VIEW' }, supported))
      .toEqual({ isolated: false, resetNonce: 5, focusNonce: 1 });
  });

  it('drops isolation when a new selection cannot be rendered', () => {
    const isolated: AnatomyViewState = { isolated: true, resetNonce: 1, focusNonce: 1 };
    expect(reduceAnatomyViewState(isolated, { type: 'SELECTION_CHANGED' }, unsupported))
      .toEqual({ isolated: false, resetNonce: 1, focusNonce: 1 });
    expect(reduceAnatomyViewState(isolated, { type: 'SELECTION_CHANGED' }, supported)).toBe(isolated);
  });
});
