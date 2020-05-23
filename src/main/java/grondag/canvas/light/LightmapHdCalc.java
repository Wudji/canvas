/*******************************************************************************
 * Copyright 2019, 2020 grondag
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package grondag.canvas.light;

import static grondag.canvas.light.LightmapHd.lightIndex;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;

final class LightmapHdCalc {
	static float input(int b, boolean isSky) {
		return b == AoFaceData.OPAQUE ? AoFaceData.OPAQUE : (isSky ? (b >> 16) & 0xFF : b & 0xFF) / 16f;
	}

	static void computeLight(int[] light, AoFaceData faceData, boolean isSky) {
		// PERF: use integer math
		final float center = input(faceData.center, isSky);
		final float top = input(faceData.top, isSky);
		final float bottom = input(faceData.bottom, isSky);
		final float right = input(faceData.right, isSky);
		final float left = input(faceData.left, isSky);
		final float topLeft = input(faceData.topLeft, isSky);
		final float topRight = input(faceData.topRight, isSky);
		final float bottomRight = input(faceData.bottomRight, isSky);
		final float bottomLeft = input(faceData.bottomLeft, isSky);

		// Note: won't work for other than 4x4 interior, 6x6 padded
		computeQuadrant(center, left, top, topLeft, light, LightmapSizer.NEG, LightmapSizer.NEG);
		computeQuadrant(center, right, top, topRight, light, LightmapSizer.POS, LightmapSizer.NEG);
		computeQuadrant(center, left, bottom, bottomLeft, light, LightmapSizer.NEG, LightmapSizer.POS);
		computeQuadrant(center, right, bottom, bottomRight, light, LightmapSizer.POS, LightmapSizer.POS);
	}

	private static void computeQuadrant(float center, float uSide, float vSide, float corner, int light[], Int2IntFunction uFunc, Int2IntFunction vFunc) {
		//FIX: handle error case when center is missing
		if(uSide == AoFaceData.OPAQUE) {
			if(vSide == AoFaceData.OPAQUE) {
				// fully enclosed
				computeOpen(center, center - 0.5f, center - 0.5f, center - 0.5f, light, uFunc, vFunc);
			} else if (corner == AoFaceData.OPAQUE) {
				// U + corner enclosing
				final float min = (center < vSide ? center : vSide) - 0.5f;
				computeOpen(center, center - 0.5f, vSide, min, light, uFunc, vFunc);
				//computeOpen(center, center - 0.5f, vSide, vSide - 0.5f, light, uFunc, vFunc);
			} else {
				// U side enclosing
				//				float min = (center < vSide ? center : vSide);
				//
				//				if (corner < min) {
				//					min = corner;
				//				}
				//
				//				min -= 0.5f;

				computeOpen(center, center - 0.5f, vSide, corner, light, uFunc, vFunc);
				//computeOpaqueU(center, vSide, corner, light, uFunc, vFunc);
			}
		} else if(vSide == AoFaceData.OPAQUE) {
			if(corner == AoFaceData.OPAQUE) {
				// V + corner enclosing
				final float min = (center < uSide ? center : uSide) - 0.5f;
				computeOpen(center, uSide, center - 0.5f, min, light, uFunc, vFunc);
				//computeOpen(center, uSide, center - 0.5f, uSide - 0.5f, light, uFunc, vFunc);
			} else {
				// V side enclosing
				//				float min = (center < uSide ? center : uSide);
				//
				//				if (corner < min) {
				//					min = corner;
				//				}
				//
				//				min -= 0.5f;

				//computeOpaqueV(center, uSide, corner, light, uFunc, vFunc);
				computeOpen(center, uSide, center - 0.5f, corner, light, uFunc, vFunc);
			}

		} else if(corner == AoFaceData.OPAQUE) {
			// opaque corner
			float min = (center < uSide ? center : uSide);

			if (vSide < min) {
				min = vSide;
			}

			min -= 0.5f;

			computeOpen(center, uSide, vSide, min, light, uFunc, vFunc);
			//			computeOpaqueCorner(center, uSide, vSide, light, uFunc, vFunc);

		} else {
			// all open
			computeOpen(center, uSide, vSide, corner, light, uFunc, vFunc);
		}
	}

	static void computeOpen(float center, float uSide, float vSide, float corner, int light[], Int2IntFunction uFunc, Int2IntFunction vFunc) {
		for(int u = 0; u < LightmapSizer.radius; u++) {
			for(int v = 0; v < LightmapSizer.radius; v++) {
				final float uLinear = 1f - LightmapSizer.centralPixelDistance - u * LightmapSizer.pixelUnitFraction;
				final float vLinear = 1f - LightmapSizer.centralPixelDistance - v * LightmapSizer.pixelUnitFraction;

				assert uLinear >= 0 && uLinear <= 1f;
				assert vLinear >= 0 && vLinear <= 1f;

				final float linear = center * (uLinear * vLinear)
						+ corner * (1 - uLinear) * (1 - vLinear)
						+ uSide * ((1 - uLinear) * (vLinear))
						+ vSide * ((uLinear) * (1 - vLinear));

				light[lightIndex(uFunc.applyAsInt(u), vFunc.applyAsInt(v))] = output(linear);
			}
		}
	}

	static void computeOpaqueU(float center, float vSide, float corner, int light[], Int2IntFunction uFunc, Int2IntFunction vFunc) {
		//  Layout  S = self, C = corner
		//  V C V
		//  S x S
		//  V C V

		final LightmapCornerHelper help = LightmapCornerHelper.prepareThreadlocal(corner, center, vSide);

		//  F G H
		//  J K L
		//  M N O
		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(0))] = output(help.o());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(0))] = output(help.n());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(0))] = output(help.m());

		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(1))] = output(help.l());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(1))] = output(help.k());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(1))] = output(help.j());

		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(2))] = output(help.h());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(2))] = output(help.g());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(2))] = output(help.f());

	}

	static void computeOpaqueV(float center, float uSide, float corner, int light[], Int2IntFunction uFunc, Int2IntFunction vFunc) {
		//  Layout  S = self, C = corner
		//  U S U
		//  C x C
		//  U S U
		//
		final LightmapCornerHelper help = LightmapCornerHelper.prepareThreadlocal(center, corner, uSide);

		//  A B C
		//  E F G
		//  I J K
		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(0))] = output(help.a());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(0))] = output(help.b());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(0))] = output(help.c());

		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(1))] = output(help.e());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(1))] = output(help.f());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(1))] = output(help.g());

		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(2))] = output(help.i());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(2))] = output(help.j());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(2))] = output(help.k());
	}

	static void computeOpaqueCorner(float center, float uSide, float vSide, int light[], Int2IntFunction uFunc, Int2IntFunction vFunc) {
		final LightmapCornerHelper help = LightmapCornerHelper.prepareThreadlocal(uSide, vSide, center);

		//  Layout
		//  U C
		//  x V

		//  B C D
		//  F G H
		//  J K L
		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(0))] = output(help.d());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(0))] = output(help.c());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(0))] = output(help.b());

		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(1))] = output(help.h());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(1))] = output(help.g());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(1))] = output(help.f());

		light[lightIndex(uFunc.applyAsInt(0), vFunc.applyAsInt(2))] = output(help.l());
		light[lightIndex(uFunc.applyAsInt(1), vFunc.applyAsInt(2))] = output(help.k());
		light[lightIndex(uFunc.applyAsInt(2), vFunc.applyAsInt(2))] = output(help.j());
	}

	static int output(float in) {
		int result = Math.round(in * 17f);

		if(result < 0) {
			result = 0;
		} else if(result > 255) {
			result = 255;
		}
		return result;
	}
}
