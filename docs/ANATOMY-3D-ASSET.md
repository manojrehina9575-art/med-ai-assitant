# BodyParts3D Anatomy Viewer Asset

The Clinical Workspace anatomy viewer now defaults to a real BodyParts3D-derived anatomy GLB for
Skeleton Viewer V1. It includes the selected skeletal structures plus detailed brain anatomy,
cranial nerve context, lungs, and kidneys.
The schematic primitive skeleton remains only as a development or asset-missing fallback and is
disclosed in the UI when used.

## Official Source

- Source: LSDB Archive, BodyParts3D database
- Database page: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/desc.html
- Download page: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/download.html
- License page verified: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html
- Download date: 2026-09-04
- Dataset/release: BodyParts3D Release 4.0, PART-OF and IS-A trees, 99% polygon-reduced OBJ archives
- PART-OF source archive: `partof_BP3D_4.0_obj_99.zip`
- PART-OF download URL: https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/partof_BP3D_4.0_obj_99.zip
- PART-OF archive SHA-256: `9fbc713fffeee924a5a657d9813d84d7eb957bded63adb854931dd5e3eb61c97`
- IS-A source archive: `isa_BP3D_4.0_obj_99.zip`
- IS-A download URL: https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/isa_BP3D_4.0_obj_99.zip
- IS-A archive SHA-256: `40665852c49f218326590e204db91064a1ecfc3c6f8cbd7bbbcaac62c7cd409e`
- Official mapping tables used:
  - https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/partof_parts_list_e.txt
  - https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/partof_element_parts.txt
  - https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/isa_parts_list_e.txt
  - https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/isa_element_parts.txt

## License And Attribution

The official LSDB Archive license page was verified on 2026-09-04. It lists "Last updated:
2025/02/27", identifies the database license as Creative Commons Attribution 4.0 International, and
requires this attribution:

`BodyParts3D, © The Database Center for Life Science licensed under CC Attribution 4.0 International`

The OBJ headers inside the Release 4.0 archive still include an older CC Attribution-Share Alike 2.1
Japan notice. The app documentation and UI use the current official LSDB Archive license page as the
source of truth, while the generated metadata records the OBJ-header notice for auditability.

## Repository Files

- Final GLB: `frontend/public/models/anatomy/bodyparts3d-skeleton-v1.glb`
- Generated metadata: `frontend/src/components/anatomy-viewer/model/bodyparts3dSkeletonModel.json`
- Viewer manifest: `frontend/src/components/anatomy-viewer/model/anatomyViewerManifest.ts`
- Conversion script: `scripts/anatomy/build-bodyparts3d-skeleton.mjs`
- Element selection spec: `scripts/anatomy/bodyparts3d-elements.json`

The complete official source archives are not committed. The converter defaults to the ignored local
cache paths:

- `.anatomy-source-cache/partof_BP3D_4.0_obj_99.zip`
- `.anatomy-source-cache/isa_BP3D_4.0_obj_99.zip`

Local source caches such as `.anatomy-source-cache/` are ignored by Git and must not be placed in
`frontend/public`.

## Reproducible Build

Run the converter against the official archives:

```bash
node scripts/anatomy/build-bodyparts3d-skeleton.mjs \
  --partof-archive /path/to/partof_BP3D_4.0_obj_99.zip \
  --isa-archive /path/to/isa_BP3D_4.0_obj_99.zip
```

For a clean verification build outside the repo:

```bash
node scripts/anatomy/build-bodyparts3d-skeleton.mjs \
  --partof-archive /path/to/partof_BP3D_4.0_obj_99.zip \
  --isa-archive /path/to/isa_BP3D_4.0_obj_99.zip \
  --out /private/tmp/bodyparts3d-skeleton-v1.glb \
  --metadata /private/tmp/bodyparts3dSkeletonModel.json
```

The clean verification build produced the same GLB SHA-256 as the repo asset:

`472ed3d0e9f28ee65a737eb4fffba31ab690737e3d116a345497fc8c2c406791`

## Transformations

The conversion preserves selected OBJ geometry and applies only documented global transforms:

- proper rotation of +90 degrees about X: source `(x, y, z)` to viewer `(x, z, -y)`
- uniform millimetres-to-viewer-units scale of `0.001`
- translation to center the assembled model bounding box
- canonical mesh names assigned in the GLB nodes/meshes
- a neutral bone, organ, nervous tissue or nerve material assigned per mesh, by its `system` field,
  for viewer highlighting

No mirroring is performed.

The source coordinate interpretation used by the converter is:

- source +X = anatomical left
- source +Y = posterior
- source +Z = superior

After conversion, anatomical right sits at negative viewer X, matching the existing focus logic.

## Selected BodyParts3D Elements

| Viewer concept | Canonical mesh | FMA concept | BP representation | BodyParts3D name | Side | Source OBJ |
|---|---|---|---|---|---|---|
| Right humerus | `Humerus_R` | `FMA23130` | `BP10197` | right humerus | RIGHT | `FJ3368.obj` |
| Left humerus | `Humerus_L` | `FMA23131` | `BP10189` | left humerus | LEFT | `FJ3262.obj` |
| Right femur | `Femur_R` | `FMA24474` | `BP10053` | right femur | RIGHT | `FJ3365.obj` |
| Left femur | `Femur_L` | `FMA24475` | `BP10115` | left femur | LEFT | `FJ3259.obj` |
| Right scapula | `Scapula_R` | `FMA13395` | `BP10144` | right scapula | RIGHT | `FJ3384.obj` |
| Left scapula | `Scapula_L` | `FMA13396` | `BP10159` | left scapula | LEFT | `FJ3279.obj` |
| Right clavicle | `Clavicle_R` | `FMA13322` | `BP10225` | right clavicle | RIGHT | `FJ3362.obj` |
| Left clavicle | `Clavicle_L` | `FMA13323` | `BP10007` | left clavicle | LEFT | `FJ3237.obj` |
| Right tibia | `Tibia_R` | `FMA24477` | `BP9559` | right tibia | RIGHT | `FJ3387.obj` |
| Left tibia | `Tibia_L` | `FMA24478` | `BP10195` | left tibia | LEFT | `FJ3282.obj` |
| Right fibula | `Fibula_R` | `FMA24480` | `BP9550` | right fibula | RIGHT | `FJ3366.obj` |
| Left fibula | `Fibula_L` | `FMA24481` | `BP9685` | left fibula | LEFT | `FJ3260.obj` |
| Right patella | `Patella_R` | `FMA24486` | `BP9739` | right patella | RIGHT | `FJ3381.obj` |
| Left patella | `Patella_L` | `FMA24487` | `BP9728` | left patella | LEFT | `FJ3275.obj` |
| Right talus | `Talus_R` | `FMA24482` | `BP9560` | right talus | RIGHT | `FJ3385.obj` |
| Left talus | `Talus_L` | `FMA24483` | `BP10186` | left talus | LEFT | `FJ3280.obj` |

Context-only meshes are also included for orientation: radius, ulna, calcaneus, hip bones, sacrum
and sternum. They are rendered but are not targeted by Skeleton Viewer V1 viewer keys.

### Brain, Cranial Nerves, Lungs, And Kidneys

Added in the same conversion, on the same license/pipeline, as real targetable non-skeletal
structures (not context-only). Lungs and kidneys use the backend's existing
`respiratory.lung.{side}` / `urinary.kidney.{side}` viewer keys. Brain is an unpaired target under
the nervous-system key `nervous.brain`. Cranial nerves are included from the official IS-A archive,
because the PART-OF archive does not include those nerve OBJ files.

| Viewer concept | Canonical mesh | Role | FMA concept | BP representation | BodyParts3D name | Side | Source elements |
|---|---|---|---|---|---|---|---|
| Right cerebral hemisphere | `CerebralHemisphere_R` | target | `FMA67292` | `BP10498` | right cerebral hemisphere | RIGHT | 19 PART-OF element files |
| Left cerebral hemisphere | `CerebralHemisphere_L` | target | `FMA61819` | `BP10491` | left cerebral hemisphere | LEFT | 19 PART-OF element files |
| Midbrain | `Midbrain` | target | `FMA61993` | `BP6705` | midbrain | MIDLINE | 7 PART-OF element files |
| Pons | `Pons` | target | `FMA67943` | `BP6703` | pons | MIDLINE | 2 PART-OF element files |
| Cerebellum | `Cerebellum` | target | `FMA67944` | `BP6702` | cerebellum | MIDLINE | 2 PART-OF element files |
| Medulla oblongata | `Medulla` | target | `FMA62004` | `BP6704` | medulla oblongata | MIDLINE | 2 PART-OF element files |
| Hypothalamus | `Hypothalamus` | target | `FMA62008` | `BP6690` | hypothalamus | MIDLINE | 4 PART-OF element files |
| Epithalamus | `Epithalamus` | target | `FMA62009` | `BP6697` | epithalamus | MIDLINE | 2 PART-OF element files |
| Third ventricle | `ThirdVentricle` | target | `FMA78454` | `BP6685` | third ventricle | MIDLINE | `FJ1730.obj` |
| Fourth ventricle | `FourthVentricle` | target | `FMA78469` | `BP6686` | fourth ventricle | MIDLINE | `FJ1731.obj` |
| Central canal of spinal cord | `CentralCanalOfSpinalCord` | context | `FMA78497` | `BP6682` | central canal of spinal cord | MIDLINE | `FJ1737.obj` |
| Right optic nerve | `OpticNerve_R` | context | `FMA50875` | `BP5708` | right optic nerve | RIGHT | 2 IS-A element files |
| Left optic nerve | `OpticNerve_L` | context | `FMA50878` | `BP5709` | left optic nerve | LEFT | 2 IS-A element files |
| Right trochlear nerve | `TrochlearNerve_R` | context | `FMA50881` | `BP4695` | right trochlear nerve | RIGHT | `FJ1381.obj` |
| Left trochlear nerve | `TrochlearNerve_L` | context | `FMA50882` | `BP4696` | left trochlear nerve | LEFT | `FJ1330.obj` |
| Cranial nerve branches | `CranialNerveBranches` | context | `FMA52570` | `BP5712` | branch of cranial nerve | MIDLINE | 26 IS-A element files |
| Right lung | `Lung_R` | target | `FMA7309` | `BP9359` | right lung | RIGHT | 156 PART-OF element files |
| Left lung | `Lung_L` | target | `FMA7310` | `BP9417` | left lung | LEFT | 124 PART-OF element files |
| Right kidney | `Kidney_R` | target | `FMA7204` | `BP10046` | right kidney | RIGHT | `FJ3147.obj` |
| Left kidney | `Kidney_L` | target | `FMA7205` | `BP10234` | left kidney | LEFT | `FJ3145.obj` |

The brain target is intentionally not a single merged blob. The split target meshes cover the same
59 PART-OF brain element files as the top-level `FMA50801` brain concept, while preserving visible
substructure. The central canal row is explicitly kept as context only: the OBJ header identifies it
as central canal of spinal cord, not the full spinal cord.

Meshes render with distinct GLTF materials (`Bone`, `Organ`, `NervousTissue`, `Nerve`) selected by
the `"system": "skeletal" | "organ" | "nervous" | "nerve"` field on each entry in
`bodyparts3d-elements.json`. The viewer preserves those source material colors for unselected
context and uses blue only for the selected finding target.

### Not Included: Heart, Liver, Stomach

BodyParts3D has geometry for these too, but the backend has no anatomy vocabulary for them yet — no
`AnatomyStructure` enum value, no `AnatomyDefinition`, no matching `AnatomicalStructure` in finding
extraction. Adding them means real backend Java changes first, not just more elements in this
pipeline. Deliberately out of scope for this pass.

## Viewer Key Contract

Backend `AnatomyTarget.viewerKey` values remain stable and are not renamed. The frontend manifest
maps them to the canonical GLB mesh names:

| viewerKey | GLB meshes |
|---|---|
| `skeleton.humerus.right` | `Humerus_R` |
| `skeleton.humerus.left` | `Humerus_L` |
| `skeleton.femur.right` | `Femur_R` |
| `skeleton.femur.left` | `Femur_L` |
| `skeleton.shoulder.right` | `Scapula_R`, `Clavicle_R`, `Humerus_R` |
| `skeleton.shoulder.left` | `Scapula_L`, `Clavicle_L`, `Humerus_L` |
| `skeleton.knee.right` | `Femur_R`, `Tibia_R`, `Patella_R` |
| `skeleton.knee.left` | `Femur_L`, `Tibia_L`, `Patella_L` |
| `skeleton.ankle.right` | `Tibia_R`, `Fibula_R`, `Talus_R` |
| `skeleton.ankle.left` | `Tibia_L`, `Fibula_L`, `Talus_L` |
| `nervous.brain` | `CerebralHemisphere_R`, `CerebralHemisphere_L`, `Midbrain`, `Pons`, `Cerebellum`, `Medulla`, `Hypothalamus`, `Epithalamus`, `ThirdVentricle`, `FourthVentricle` |
| `respiratory.lung.right` | `Lung_R` |
| `respiratory.lung.left` | `Lung_L` |
| `urinary.kidney.right` | `Kidney_R` |
| `urinary.kidney.left` | `Kidney_L` |

Joints are intentionally multi-mesh targets:

- Shoulder = scapula, clavicle, humerus
- Knee = femur, tibia, patella
- Ankle = tibia, fibula, talus

## Validation Results

Conversion command output as of the detailed brain and cranial nerve addition (2026-09-06):

- source archive size: 62 MB PART-OF, 136 MB IS-A
- selected element files: 402 total (369 PART-OF, 33 IS-A)
- final GLB size: 11,561,904 bytes, shown as 11.56 MB
- GLB SHA-256: `472ed3d0e9f28ee65a737eb4fffba31ab690737e3d116a345497fc8c2c406791`
- mesh count: 46
- targetable mesh count: 30
- vertex count: 332,048
- triangle count: 593,672
- model height: 1.699 viewer units
- side check: 34 sided meshes verified
- detailed brain target: 59 PART-OF source elements split into 10 target meshes
- cranial nerve context: 33 IS-A source elements split into 5 context meshes

Prior baseline (single merged brain before detailed brain and nerve context):

- selected element files: 369
- final GLB size: 12,639,352 bytes, shown as 12.64 MB
- GLB SHA-256: `688f1e2ea2c31b59df779ec201836b25b29d13a75ade18e43657d1a033060ac3`
- mesh count: 31, targetable: 21, side check: 28 sided meshes verified

Prior baseline (before the brain addition):

- selected element files: 310
- final GLB size: 4,481,700 bytes, shown as 4.48 MB
- GLB SHA-256: `e871e71456d5d2b5588bd41eba9fda08da067ffd769b08deb979706c2e5d3a55`
- mesh count: 30, targetable: 20, side check: 28 sided meshes verified

All target side centroids passed the right/left check:

- `_R` meshes have negative X centroid after conversion
- `_L` meshes have positive X centroid after conversion
- midline brain structures are `side: "M"`, so they are not included in the left/right side check

The frontend model metadata tests assert that every manifest mesh exists in the generated model,
right and left never resolve to the same mesh, joint targets contain the expected mesh groups, and
missing/duplicate mesh validation reports problems without breaking the Clinical Workspace.
