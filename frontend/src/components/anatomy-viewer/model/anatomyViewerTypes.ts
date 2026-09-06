/**
 * Types for the Skeleton Viewer V1.
 *
 * The viewer is driven exclusively by the backend's stable `viewerKey`. Display strings such as
 * "Right proximal humerus" are presentation only and are never parsed to find a mesh.
 */

/** Coarse anatomical zone used for camera framing. Not a clinical sub-structure. */
export type AnatomyFocusRegion =
  | 'right_upper_arm'
  | 'left_upper_arm'
  | 'right_thigh'
  | 'left_thigh'
  | 'right_shoulder_girdle'
  | 'left_shoulder_girdle'
  | 'right_knee'
  | 'left_knee'
  | 'right_ankle'
  | 'left_ankle'
  | 'head'
  | 'right_chest'
  | 'left_chest'
  | 'right_flank'
  | 'left_flank';

export interface AnatomyViewerEntry {
  /** Mesh names expected in the skeleton asset. Several for joints spanning multiple bones. */
  meshNames: string[];
  /** Camera framing zone for this structure. */
  focusRegion: AnatomyFocusRegion;
  /** Structures this entry covers, for honest UI copy. Never used for mesh lookup. */
  coveredStructures: string[];
}

export type AnatomyViewerUnsupportedReason =
  /** The mapped finding has no single structure (bilateral or unspecified laterality). */
  | 'NO_VIEWER_KEY'
  /** A real anatomy target, but outside the skeletal system this viewer renders. */
  | 'NON_SKELETAL_SYSTEM'
  /** A skeletal key with no manifest entry yet, or an unrecognised namespace. */
  | 'UNMAPPED_VIEWER_KEY';

export interface SupportedAnatomyViewerTarget {
  supported: true;
  viewerKey: string;
  meshNames: string[];
  focusRegion: AnatomyFocusRegion;
  coveredStructures: string[];
}

export interface UnsupportedAnatomyViewerTarget {
  supported: false;
  viewerKey: string | null;
  reason: AnatomyViewerUnsupportedReason;
  /** Reviewer-facing copy. Never claims a model exists. */
  message: string;
}

export type AnatomyViewerTarget = SupportedAnatomyViewerTarget | UnsupportedAnatomyViewerTarget;
