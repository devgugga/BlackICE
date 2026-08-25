export type ViewerPhase = 'IDLE' | 'LOADING_STUDY' | 'READY' | 'LOADING_SERIES' | 'ERROR';

export type ViewerTool = 'WINDOW_LEVEL' | 'ZOOM' | 'PAN' | 'STACK_SCROLL' | 'LENGTH';

export type SeriesAvailability = 'SUPPORTED' | 'UNSUPPORTED';

export type UnsupportedReason =
  | 'MULTI_FRAME'
  | 'NON_IMAGE_OBJECT'
  | 'IMAGE_SOP_CLASS_UNSUPPORTED'
  | null;

export interface ViewerSeriesSummary {
  readonly seriesInstanceUid: string;
  readonly seriesNumber: number | null;
  readonly modality: string;
  readonly description: string | null;
  readonly instanceCount: number;
  readonly availability: SeriesAvailability;
  readonly unsupportedReason: UnsupportedReason;
}

export interface StudyViewerSummary {
  readonly studyInstanceUid: string;
  readonly patientName: string | null;
  readonly patientId: string | null;
  readonly patientIdIssuer: string | null;
  readonly studyDate: string | null;
  readonly studyTime: string | null;
  readonly description: string | null;
  readonly series: readonly ViewerSeriesSummary[];
}

export interface ViewerInstance {
  readonly sopInstanceUid: string;
  readonly sopClassUid: string;
  readonly instanceNumber: number | null;
  readonly rows: number;
  readonly columns: number;
  readonly samplesPerPixel: number;
  readonly photometricInterpretation: string;
  readonly bitsAllocated: number;
  readonly bitsStored: number;
  readonly highBit: number;
  readonly pixelRepresentation: number;
  readonly planarConfiguration: number | null;
  readonly imagePositionPatient: readonly number[] | null;
  readonly imageOrientationPatient: readonly number[] | null;
  readonly pixelSpacing: readonly number[] | null;
  readonly frameOfReferenceUid: string | null;
  readonly rescaleIntercept: number | null;
  readonly rescaleSlope: number | null;
  readonly windowCenter: readonly number[] | null;
  readonly windowWidth: readonly number[] | null;
}

export interface ViewerSeriesInstances {
  readonly studyInstanceUid: string;
  readonly seriesInstanceUid: string;
  readonly instances: readonly ViewerInstance[];
}

export interface StudyViewerController {
  loadStudy(studyUid: string): Promise<void>;
  activateSeries(seriesUid?: string): Promise<void>;
  deactivateSeries(): void;
  selectSeries(seriesUid: string, activate: boolean): Promise<void>;
  dispose(): void;
}
