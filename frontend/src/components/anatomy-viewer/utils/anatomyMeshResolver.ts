import {
  SUPPORTED_VIEWER_KEY_PREFIXES,
  anatomyViewerManifest,
} from '../model/anatomyViewerManifest';
import type { AnatomyViewerTarget } from '../model/anatomyViewerTypes';

/**
 * Normalises a mesh name for lookup: case, underscores, hyphens and whitespace are ignored, so a
 * manifest entry survives cosmetic differences between assets.
 */
export function normalizeMeshName(name: string): string {
  return name.toLowerCase().replace(/[\s_\-.]/g, '');
}

export function isSupportedViewerKeyNamespace(viewerKey: string): boolean {
  return SUPPORTED_VIEWER_KEY_PREFIXES.some((prefix) => viewerKey.startsWith(prefix));
}

/**
 * Resolves a backend `viewerKey` to the meshes the viewer should act on.
 *
 * Every unsupported outcome is explicit and carries reviewer-facing copy. The viewer never
 * substitutes a nearby structure or guesses a side.
 */
export function resolveViewerTarget(viewerKey?: string | null): AnatomyViewerTarget {
  if (!viewerKey || !viewerKey.trim()) {
    return {
      supported: false,
      viewerKey: null,
      reason: 'NO_VIEWER_KEY',
      message: 'No single 3D structure is available for this mapped finding.',
    };
  }

  const key = viewerKey.trim();
  if (!isSupportedViewerKeyNamespace(key)) {
    return {
      supported: false,
      viewerKey: key,
      reason: 'NON_SKELETAL_SYSTEM',
      message: '3D model not available for this structure yet.',
    };
  }

  const entry = anatomyViewerManifest[key];
  if (!entry) {
    return {
      supported: false,
      viewerKey: key,
      reason: 'UNMAPPED_VIEWER_KEY',
      message: '3D model not available for this structure yet.',
    };
  }

  return {
    supported: true,
    viewerKey: key,
    meshNames: [...entry.meshNames],
    focusRegion: entry.focusRegion,
    coveredStructures: [...entry.coveredStructures],
  };
}

/** Normalised mesh names for a resolved target, for object-graph matching. */
export function normalizedMeshNames(meshNames: string[]): Set<string> {
  return new Set(meshNames.map(normalizeMeshName));
}
