import type { FileType } from '@/types';

export type ClinicalReportStatus = 'DRAFT' | 'REVIEW_REQUIRED' | 'READY_TO_SIGN' | 'SIGNED';
export type QaSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type QaIssueType =
  | 'LATERALITY_CONFLICT'
  | 'COMPARISON_GAP'
  | 'MISSING_MEASUREMENT'
  | 'RECOMMENDATION_REVIEW'
  | 'DOCUMENTATION_NOTE';
export type QaStatus = 'NO_ISSUES' | 'REVIEW_RECOMMENDED';
export type QaRequestStatus = 'IDLE' | 'LOADING' | 'SUCCESS' | 'ERROR';
export type AnatomySide = 'LEFT' | 'RIGHT' | 'MIDLINE' | 'BILATERAL' | 'UNSPECIFIED';
export type ClinicalContextTab = 'clinical-workspace' | 'prior-studies' | 'timeline' | 'audit';
export type FindingChangeType =
  | 'NEW'
  | 'RESOLVED'
  | 'UNCHANGED'
  | 'INCREASED'
  | 'DECREASED'
  | 'CHANGED'
  | 'INDETERMINATE';
export type LongitudinalRequestStatus = 'IDLE' | 'LOADING' | 'SUCCESS' | 'ERROR';

export interface ClinicalWorkspacePatient {
  id: string;
  fullName: string;
  medicalRecordNumber: string;
  dateOfBirth: string;
  ageLabel: string;
}

export interface ClinicalWorkspaceStudy {
  id: string;
  patient: ClinicalWorkspacePatient;
  accessionNumber: string;
  studyType: string;
  modality: string;
  fileType: FileType;
  studyDate: string;
  reportStatus: ClinicalReportStatus;
}

export interface ReportSection {
  id: 'findings' | 'comparison' | 'impression';
  title: string;
  body: string[];
}

export interface DraftReport {
  id: string;
  radiologist: string;
  createdAt: string;
  sections: ReportSection[];
  /** Raw metadata for the Report Metadata footer. Optional: absent while a review is still loading. */
  metadata?: DraftReportMetadata;
}

export interface DraftReportMetadata {
  reportStatus: ClinicalReportStatus;
  createdAt: string;
  createdBy: string | null;
  lastUpdatedAt: string | null;
  lastUpdatedLabel: string;
}

export interface QaEvidence {
  label: string;
  normalizedLabel?: string;
  text: string;
}

export type AnatomySystem = 'SKELETAL' | 'NERVOUS' | 'RESPIRATORY' | 'URINARY' | 'OTHER';

/** Stable machine-readable anatomy target resolved by the backend anatomy layer. */
export interface AnatomyTarget {
  system: AnatomySystem;
  structureCode: string;
  displayName: string;
  side: AnatomySide;
  region: string;
  /** Null whenever no single unambiguous structure can be named (unspecified or bilateral side). */
  viewerKey?: string | null;
  parentStructureCode?: string | null;
  sourceAnatomy?: string | null;
}

/** Where a selected anatomy target was chosen from. View state only — never sent to the backend. */
export type AnatomySelectionSource = 'QA' | 'LONGITUDINAL_CURRENT' | 'LONGITUDINAL_PRIOR';

export interface AnatomySelection {
  structure: string;
  displayName: string;
  side: AnatomySide;
  region: string;
  system: string;
  /** Stable viewer key from the backend, when one exists. Not user-facing copy. */
  viewerKey?: string | null;
  /** Report section or report this mapped structure came from, e.g. "Findings", "Prior report". */
  sourceLabel?: string | null;
  /** Source finding text this structure was mapped from. */
  sourceText?: string | null;
  /** Which workflow the selection came from. Defaults to QA when absent. */
  sourceKind?: AnatomySelectionSource;
  /** Longitudinal change label, when the selection came from a comparison. */
  comparisonLabel?: string | null;
}

export interface QaIssue {
  id: string;
  severity: QaSeverity;
  type: QaIssueType;
  message: string;
  recommendation: string;
  evidence: QaEvidence[];
  /** Default anatomy target for this issue. For a laterality conflict this is the Findings side. */
  anatomySelection?: AnatomySelection;
  /**
   * Every anatomy target this issue points at. A laterality conflict has one per conflicting
   * section, and the system does not claim which one is clinically correct.
   */
  anatomyCandidates?: AnatomySelection[];
}

export interface ReportQaIssue {
  id: string;
  type: QaIssueType;
  severity: QaSeverity;
  message: string;
  findingText?: string | null;
  impressionText?: string | null;
  sectionA?: string | null;
  sectionB?: string | null;
  sideA?: 'RIGHT' | 'LEFT' | null;
  sideB?: 'RIGHT' | 'LEFT' | null;
  anatomyCode?: string | null;
  region?: string | null;
  confidence?: number | null;
  detector?: string | null;
  detectorVersion?: string | null;
  evidence?: ReportQaEvidence[] | null;
}

export interface ReportQaEvidence {
  sourceSection: 'FINDINGS' | 'COMPARISON' | 'IMPRESSION' | 'UNKNOWN';
  findingType: 'ANEURYSM' | 'FRACTURE' | 'LESION' | 'NODULE' | 'EFFUSION' | 'MASS' | 'DISLOCATION';
  anatomy?: 'HUMERUS' | 'FEMUR' | 'BRAIN' | 'KIDNEY' | 'LUNG' | 'PLEURA' | 'SHOULDER' | 'ANKLE' | 'KNEE' | null;
  anatomyText?: string | null;
  side: 'RIGHT' | 'LEFT' | 'BILATERAL' | 'UNSPECIFIED';
  region: 'PROXIMAL' | 'DISTAL' | 'MID' | 'UPPER' | 'LOWER' | 'APICAL' | 'BASAL' | 'UNSPECIFIED';
  status: 'PRESENT' | 'ABSENT';
  certainty: 'ASSERTED' | 'POSSIBLE' | 'SUSPECTED';
  sourceText: string;
  /** Optional; absent when the backend anatomy layer could not resolve a target safely. */
  anatomyTarget?: AnatomyTarget | null;
}

export interface ReportQaResult {
  reportId: string;
  status: QaStatus;
  issues: ReportQaIssue[];
  issueCount: number;
  evaluatedAt: string;
}

export interface PriorStudy {
  id: string;
  date: string;
  studyType: string;
  modality: string;
  summary: string;
}

export interface StructuredFinding {
  id: string;
  label: string;
  structure: string;
  side?: AnatomySide;
  region?: string;
  summary: string;
}

export interface LongitudinalStructuredFinding {
  id: string;
  findingType: 'ANEURYSM' | 'FRACTURE' | 'LESION' | 'NODULE' | 'EFFUSION' | 'MASS' | 'DISLOCATION';
  anatomy?: 'HUMERUS' | 'FEMUR' | 'BRAIN' | 'KIDNEY' | 'LUNG' | 'PLEURA' | 'SHOULDER' | 'ANKLE' | 'KNEE' | null;
  anatomyText?: string | null;
  side: 'RIGHT' | 'LEFT' | 'BILATERAL' | 'UNSPECIFIED';
  region: 'PROXIMAL' | 'DISTAL' | 'MID' | 'UPPER' | 'LOWER' | 'APICAL' | 'BASAL' | 'UNSPECIFIED';
  status: 'PRESENT' | 'ABSENT';
  certainty: 'ASSERTED' | 'POSSIBLE' | 'SUSPECTED';
  measurement?: number | null;
  unit?: 'mm' | 'cm' | string | null;
  sourceSection: 'FINDINGS' | 'COMPARISON' | 'IMPRESSION' | 'UNKNOWN';
  sourceText: string;
}

export interface FindingComparison {
  currentFinding?: LongitudinalStructuredFinding | null;
  priorFinding?: LongitudinalStructuredFinding | null;
  /** Shared anatomy vocabulary; absent when the finding is missing or its anatomy is uncatalogued. */
  currentAnatomyTarget?: AnatomyTarget | null;
  priorAnatomyTarget?: AnatomyTarget | null;
  changeType: FindingChangeType;
  priorMeasurementMm?: number | null;
  currentMeasurementMm?: number | null;
  measurementDeltaMm?: number | null;
  explanation: string;
}

export interface LongitudinalSummary {
  newFindings: number;
  resolvedFindings: number;
  increasedFindings: number;
  decreasedFindings: number;
  unchangedFindings: number;
  changedFindings: number;
  indeterminateFindings: number;
}

export interface LongitudinalResult {
  currentReportId: string;
  priorReportId: string;
  comparisons: FindingComparison[];
  summary: LongitudinalSummary;
  evaluatedAt: string;
}

export interface TimelineEvent {
  id: string;
  time: string;
  label: string;
  detail?: string;
}

export interface AuditEvent {
  id: string;
  label: string;
  actor?: string;
  time: string;
  detail?: string;
}

export interface ClinicalWorkspaceDemoCase {
  study: ClinicalWorkspaceStudy;
  report: DraftReport;
  qaIssues: QaIssue[];
  defaultAnatomySelection: AnatomySelection;
  priorStudies: PriorStudy[];
  structuredFindings: StructuredFinding[];
  timeline: TimelineEvent[];
  audit: AuditEvent[];
}
