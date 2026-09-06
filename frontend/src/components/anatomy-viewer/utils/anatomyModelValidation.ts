import { Mesh, type Object3D } from 'three';
import { anatomyViewerManifest } from '../model/anatomyViewerManifest';
import type { AnatomyViewerEntry } from '../model/anatomyViewerTypes';
import { normalizeMeshName } from './anatomyMeshResolver';

export interface AnatomyModelValidation {
  /** Manifest mesh names with no match in the model, as `viewerKey -> mesh name`. */
  missingMeshes: { viewerKey: string; meshName: string }[];
  /** Mesh names appearing more than once in the model; highlighting them is ambiguous. */
  duplicateNames: string[];
  /** Viewer keys with at least one matching mesh — these can be highlighted and framed. */
  supportedViewerKeys: string[];
  /** Viewer keys with no matching mesh at all. */
  invalidViewerKeys: string[];
}

type Manifest = Readonly<Record<string, AnatomyViewerEntry>>;

/**
 * Checks a loaded model's mesh inventory against the viewer manifest.
 *
 * Pure and WebGL-free: it takes mesh names, so the same check runs over a loaded three.js scene at
 * runtime and over the build's committed model metadata in tests.
 *
 * A missing mesh is reported, never thrown — one absent bone must not take down the workspace.
 */
export function validateAnatomyMeshNames(
  meshNames: string[],
  manifest: Manifest = anatomyViewerManifest
): AnatomyModelValidation {
  const seen = new Map<string, number>();
  for (const name of meshNames) {
    const key = normalizeMeshName(name);
    seen.set(key, (seen.get(key) ?? 0) + 1);
  }

  const duplicateNames = meshNames.filter((name, index) => {
    const key = normalizeMeshName(name);
    return (seen.get(key) ?? 0) > 1 && meshNames.findIndex((other) => normalizeMeshName(other) === key) === index;
  });

  const missingMeshes: { viewerKey: string; meshName: string }[] = [];
  const supportedViewerKeys: string[] = [];
  const invalidViewerKeys: string[] = [];

  for (const [viewerKey, entry] of Object.entries(manifest)) {
    const present = entry.meshNames.filter((meshName) => seen.has(normalizeMeshName(meshName)));
    const missing = entry.meshNames.filter((meshName) => !seen.has(normalizeMeshName(meshName)));
    for (const meshName of missing) missingMeshes.push({ viewerKey, meshName });

    // A partially present joint can still render useful context, but the absent required meshes are
    // reported above. A key is invalid only when none of its required meshes exists.
    if (present.length === 0) {
      invalidViewerKeys.push(viewerKey);
      continue;
    }
    supportedViewerKeys.push(viewerKey);
  }

  return { missingMeshes, duplicateNames, supportedViewerKeys, invalidViewerKeys };
}

/** Collects mesh names from a loaded object graph. */
export function meshNamesOf(root: Object3D): string[] {
  const names: string[] = [];
  root.traverse((object) => {
    if (object instanceof Mesh && object.name) names.push(object.name);
  });
  return names;
}

export function validateAnatomyModel(
  root: Object3D,
  manifest: Manifest = anatomyViewerManifest
): AnatomyModelValidation {
  return validateAnatomyMeshNames(meshNamesOf(root), manifest);
}

/** Development-only diagnostic. Warns loudly, changes nothing, never throws. */
export function warnOnAnatomyModelProblems(validation: AnatomyModelValidation, modelUrl: string): void {
  if (
    validation.missingMeshes.length === 0 &&
    validation.invalidViewerKeys.length === 0 &&
    validation.duplicateNames.length === 0
  ) return;

  if (validation.missingMeshes.length > 0) {
    console.warn(
      `[anatomy-viewer] ${modelUrl} is missing ${validation.missingMeshes.length} manifest ` +
        `mesh reference(s): ${validation.missingMeshes
          .map((entry) => `${entry.viewerKey} -> ${entry.meshName}`)
          .join(', ')}. ` +
        (validation.invalidViewerKeys.length > 0
          ? `No mesh exists for viewer key(s): ${validation.invalidViewerKeys.join(', ')}.`
          : 'Affected joint selections may render partial context.')
    );
  }
  if (validation.duplicateNames.length > 0) {
    console.warn(
      `[anatomy-viewer] ${modelUrl} contains duplicate mesh names: ` +
        `${validation.duplicateNames.join(', ')}. Highlighting is ambiguous for those structures.`
    );
  }
}
