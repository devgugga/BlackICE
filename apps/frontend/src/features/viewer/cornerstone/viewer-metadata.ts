import { metaData } from '@cornerstonejs/core';
import type { ViewerInstance } from '../viewer.types';

export function buildImageId(studyUid: string, seriesUid: string, sopUid: string): string {
  return `wadors:/api/dicomweb/studies/${studyUid}/series/${seriesUid}/instances/${sopUid}/frames/1`;
}

interface StoredInstanceMeta {
  readonly instance: ViewerInstance;
  readonly studyUid: string;
  readonly seriesUid: string;
}

const metadataRegistry = new Map<string, StoredInstanceMeta>();
let providerRegistered = false;

export function getViewerMetadata(type: string, imageId: string): unknown {
  const entry = metadataRegistry.get(imageId);
  if (!entry) {
    return undefined;
  }

  const { instance, seriesUid } = entry;

  switch (type) {
    case 'imagePixelModule': {
      return {
        samplesPerPixel: instance.samplesPerPixel,
        photometricInterpretation: instance.photometricInterpretation,
        rows: instance.rows,
        columns: instance.columns,
        bitsAllocated: instance.bitsAllocated,
        bitsStored: instance.bitsStored,
        highBit: instance.highBit,
        pixelRepresentation: instance.pixelRepresentation,
        planarConfiguration: instance.planarConfiguration ?? undefined,
        pixelSpacing: instance.pixelSpacing
          ? [instance.pixelSpacing[0], instance.pixelSpacing[1]]
          : undefined,
      };
    }

    case 'imagePlaneModule': {
      const hasSpacing = Boolean(instance.pixelSpacing && instance.pixelSpacing.length >= 2);
      const pixelSpacing: [number, number] = hasSpacing
        ? [instance.pixelSpacing![0], instance.pixelSpacing![1]]
        : [1, 1];

      return {
        frameOfReferenceUID: instance.frameOfReferenceUid ?? undefined,
        rows: instance.rows,
        columns: instance.columns,
        imageOrientationPatient: instance.imageOrientationPatient
          ? [...instance.imageOrientationPatient]
          : undefined,
        rowCosines: instance.imageOrientationPatient
          ? instance.imageOrientationPatient.slice(0, 3)
          : undefined,
        columnCosines: instance.imageOrientationPatient
          ? instance.imageOrientationPatient.slice(3, 6)
          : undefined,
        imagePositionPatient: instance.imagePositionPatient
          ? [...instance.imagePositionPatient]
          : undefined,
        pixelSpacing,
        rowPixelSpacing: pixelSpacing[0],
        columnPixelSpacing: pixelSpacing[1],
        hasPixelSpacing: hasSpacing,
        usingDefaultValues: !hasSpacing,
      };
    }

    case 'voiLutModule': {
      return {
        windowCenter: instance.windowCenter ? [...instance.windowCenter] : undefined,
        windowWidth: instance.windowWidth ? [...instance.windowWidth] : undefined,
      };
    }

    case 'modalityLutModule': {
      return {
        rescaleIntercept: instance.rescaleIntercept ?? 0,
        rescaleSlope: instance.rescaleSlope ?? 1,
      };
    }

    case 'generalSeriesModule': {
      return {
        seriesInstanceUID: seriesUid,
      };
    }

    case 'sopCommonModule': {
      return {
        sopClassUID: instance.sopClassUid,
        sopInstanceUID: instance.sopInstanceUid,
      };
    }

    default:
      return undefined;
  }
}

export function registerInstancesMetadata(
  instances: readonly ViewerInstance[],
  studyUid: string,
  seriesUid: string,
): string[] {
  const imageIds: string[] = [];

  for (const instance of instances) {
    const imageId = buildImageId(studyUid, seriesUid, instance.sopInstanceUid);
    metadataRegistry.set(imageId, { instance, studyUid, seriesUid });
    imageIds.push(imageId);
  }

  return imageIds;
}

export function clearMetadata(imageIds?: readonly string[]): void {
  if (imageIds) {
    for (const imageId of imageIds) {
      metadataRegistry.delete(imageId);
    }
  } else {
    metadataRegistry.clear();
  }
}

export function initMetadataProvider(): void {
  if (!providerRegistered) {
    metaData.addProvider(getViewerMetadata, 10000);
    providerRegistered = true;
  }
}
