import {
  skeletonModelAttribution,
  skeletonModelLicense,
  skeletonModelLicenseUrl,
  skeletonModelUrl,
} from './model/anatomyViewerManifest';

/**
 * Attribution surface for the bundled skeleton asset.
 *
 * The bundled model is derived from BodyParts3D, which is licensed CC BY 4.0 and requires the
 * credit below wherever the data is used. It is rendered in the viewer itself rather than being
 * buried in source comments, and the text comes from the build's recorded metadata, never from a
 * string invented here.
 */
export function AnatomyAttribution() {
  if (!skeletonModelUrl) {
    return (
      <p className="text-[10px] leading-4 text-slate-500">
        Schematic development placeholder. No licensed anatomy asset is loaded.
      </p>
    );
  }

  if (!skeletonModelAttribution) {
    return (
      <p className="text-[10px] leading-4 text-amber-300/80">
        Anatomy model attribution is not configured. Set the attribution required by the asset
        license before release.
      </p>
    );
  }

  return (
    <p className="text-[10px] leading-4 text-slate-500">
      {skeletonModelAttribution}
      {skeletonModelLicense ? (
        <>
          {' — '}
          <a
            href={skeletonModelLicenseUrl}
            target="_blank"
            rel="noreferrer noopener"
            className="underline decoration-dotted hover:text-slate-300"
          >
            {skeletonModelLicense}
          </a>
        </>
      ) : null}
    </p>
  );
}
