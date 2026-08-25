import { describe, it, expect, beforeEach } from 'vitest';
import {
  buildImageId,
  registerInstancesMetadata,
  getViewerMetadata,
  clearMetadata,
  initMetadataProvider,
} from './viewer-metadata';
import type { ViewerInstance } from '../viewer.types';

describe('viewer-metadata', () => {
  const STUDY_UID = '1.2.840.10008.1.1';
  const SERIES_UID = '1.2.840.10008.1.2';
  const SOP_UID = '1.2.840.10008.1.3';

  const baseInstance: ViewerInstance = {
    sopInstanceUid: SOP_UID,
    sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
    instanceNumber: 1,
    rows: 512,
    columns: 512,
    samplesPerPixel: 1,
    photometricInterpretation: 'MONOCHROME2',
    bitsAllocated: 16,
    bitsStored: 12,
    highBit: 11,
    pixelRepresentation: 0,
    planarConfiguration: null,
    imagePositionPatient: [-125, -250, 100],
    imageOrientationPatient: [1, 0, 0, 0, 1, 0],
    pixelSpacing: [0.75, 0.75],
    frameOfReferenceUid: '1.2.840.10008.1.99',
    rescaleIntercept: -1024,
    rescaleSlope: 1,
    windowCenter: [40],
    windowWidth: [400],
  };

  beforeEach(() => {
    clearMetadata();
  });

  describe('buildImageId', () => {
    it('constructs correct WADO-RS image ID matching DICOMweb endpoint', () => {
      expect(buildImageId(STUDY_UID, SERIES_UID, SOP_UID)).toBe(
        `wadors:/api/dicomweb/studies/${STUDY_UID}/series/${SERIES_UID}/instances/${SOP_UID}/frames/1`,
      );
    });
  });

  describe('metadata provider with valid pixelSpacing', () => {
    it('provides imagePixelModule, imagePlaneModule, voiLutModule, modalityLutModule', () => {
      const imageIds = registerInstancesMetadata([baseInstance], STUDY_UID, SERIES_UID);
      const imageId = imageIds[0];

      const pixelModule = getViewerMetadata('imagePixelModule', imageId) as any;
      expect(pixelModule).toEqual({
        samplesPerPixel: 1,
        photometricInterpretation: 'MONOCHROME2',
        rows: 512,
        columns: 512,
        bitsAllocated: 16,
        bitsStored: 12,
        highBit: 11,
        pixelRepresentation: 0,
        planarConfiguration: undefined,
        pixelSpacing: [0.75, 0.75],
      });

      const planeModule = getViewerMetadata('imagePlaneModule', imageId) as any;
      expect(planeModule).toEqual({
        frameOfReferenceUID: '1.2.840.10008.1.99',
        rows: 512,
        columns: 512,
        imageOrientationPatient: [1, 0, 0, 0, 1, 0],
        rowCosines: [1, 0, 0],
        columnCosines: [0, 1, 0],
        imagePositionPatient: [-125, -250, 100],
        pixelSpacing: [0.75, 0.75],
        rowPixelSpacing: 0.75,
        columnPixelSpacing: 0.75,
        hasPixelSpacing: true,
        usingDefaultValues: false,
      });

      const voiModule = getViewerMetadata('voiLutModule', imageId) as any;
      expect(voiModule).toEqual({
        windowCenter: [40],
        windowWidth: [400],
      });

      const modalityModule = getViewerMetadata('modalityLutModule', imageId) as any;
      expect(modalityModule).toEqual({
        rescaleIntercept: -1024,
        rescaleSlope: 1,
      });
    });
  });

  describe('metadata provider with null pixelSpacing', () => {
    it('maps missing pixelSpacing to unit computational spacing with hasPixelSpacing: false and usingDefaultValues: true', () => {
      const unspacedInstance: ViewerInstance = {
        ...baseInstance,
        pixelSpacing: null,
      };

      const imageIds = registerInstancesMetadata([unspacedInstance], STUDY_UID, SERIES_UID);
      const imageId = imageIds[0];

      const planeModule = getViewerMetadata('imagePlaneModule', imageId) as any;
      expect(planeModule.hasPixelSpacing).toBe(false);
      expect(planeModule.usingDefaultValues).toBe(true);
      expect(planeModule.pixelSpacing).toEqual([1, 1]);
      expect(planeModule.rowPixelSpacing).toBe(1);
      expect(planeModule.columnPixelSpacing).toBe(1);

      const pixelModule = getViewerMetadata('imagePixelModule', imageId) as any;
      expect(pixelModule.pixelSpacing).toBeUndefined();
    });
  });

  describe('metadata clearing and unknown queries', () => {
    it('returns undefined for unregistered imageIds or unknown module types', () => {
      expect(getViewerMetadata('imagePlaneModule', 'wadors:/unknown')).toBeUndefined();
      expect(getViewerMetadata('unknownModule', buildImageId(STUDY_UID, SERIES_UID, SOP_UID))).toBeUndefined();
    });

    it('clears registered metadata when requested', () => {
      const imageIds = registerInstancesMetadata([baseInstance], STUDY_UID, SERIES_UID);
      expect(getViewerMetadata('imagePixelModule', imageIds[0])).toBeDefined();

      clearMetadata(imageIds);
      expect(getViewerMetadata('imagePixelModule', imageIds[0])).toBeUndefined();
    });
  });

  describe('provider registration', () => {
    it('registers provider with Cornerstone without throwing', () => {
      expect(() => initMetadataProvider()).not.toThrow();
    });
  });
});
