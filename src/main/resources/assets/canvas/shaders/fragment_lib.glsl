vec4 colorAndLightmap(vec4 fragmentColor,  int layerIndex, vec4 light) {
    return bitValue(v_flags.x, layerIndex) == 0 ? light * fragmentColor : u_emissiveColor * fragmentColor;
}

vec4 aoFactor(vec2 lightCoord) {
// Don't apply AO for item renders
#if CONTEXT_IS_BLOCK && !ENABLE_SMOOTH_LIGHT
    float ao = v_ao;

    #if AO_SHADING_MODE == AO_MODE_SUBTLE_BLOCK_LIGHT || AO_SHADING_MODE == AO_MODE_SUBTLE_ALWAYS
        // accelerate the transition from 0.4 (should be the minimum) to 1.0
        float bao = (ao - 0.4) / 0.6;
        bao = clamp(bao, 0.0, 1.0);
        bao = 1.0 - bao;
        bao = bao * bao * (1.0 - lightCoord.x * 0.6);
        bao = 0.4 + (1.0 - bao) * 0.6;

        #if AO_SHADING_MODE == AO_MODE_SUBTLE_ALWAYS
            return vec4(bao, bao, bao, 1.0);
        #else
            ao = mix(ao, bao, lightCoord.x);
            return vec4(ao, ao, ao, 1.0);
        #endif
    #else
        return vec4(ao, ao, ao, 1.0);
    #endif
#else
    return vec4(1.0, 1.0, 1.0, 1.0);
#endif
}

float effectModifier() {
    return u_world[WORLD_EFFECT_MODIFIER];
}

vec2 lightCoord() {
#if ENABLE_SMOOTH_LIGHT
    vec4 hd = texture2D(u_utility, v_hd_lightmap);
    // PERF: return directly vs extra math below
    vec2 lightCoord = vec2(hd.r, hd.a) * 15.0;

    #if ENABLE_LIGHT_NOISE
        vec4 dither = texture2D(u_dither, gl_FragCoord.xy / 8.0);
        lightCoord += dither.r / 64.0 - (1.0 / 128.0);
    #endif

    return (lightCoord + 0.5) / 16.0;
#else
    return v_lightcoord;
#endif
}

vec4 diffuseColor() {

    #if !CONTEXT_IS_GUI
        vec2 lightCoord = lightCoord();
    #endif

    #if CONTEXT_IS_BLOCK
        vec4 light = texture2D(u_lightmap, lightCoord);
    #elif CONTEXT_IS_GUI
        vec4 light = vec4(1.0, 1.0, 1.0, 1.0);
    #else
        vec4 light = texture2D(u_lightmap, v_lightcoord);
    #endif

    #if HARDCORE_DARKNESS
        if(u_world[WORLD_HAS_SKYLIGHT] == 1.0 && u_world[WORLD_NIGHT_VISION] == 0.0) {
            float floor = u_world[WOLRD_MOON_SIZE] * lightCoord.y;
            float dark = 1.0 - smoothstep(0.0, 0.8, 1.0 - luminance(light.rgb));
            dark = max(floor, dark);
            light *= vec4(dark, dark, dark, 1.0);
        }
    #endif

    #if !ENABLE_SMOOTH_LIGHT && AO_SHADING_MODE != AO_MODE_NONE && CONTEXT_IS_BLOCK
        vec4 aoFactor = aoFactor(lightCoord);
    #endif

    #if DIFFUSE_SHADING_MODE == DIFFUSE_MODE_SKY_ONLY && CONTEXT_IS_BLOCK
        vec4 diffuse;
        if(u_world[WORLD_HAS_SKYLIGHT] == 1.0 && u_world[WORLD_NIGHT_VISION] == 0) {
            float d = 1.0 - v_diffuse;
            d *= u_world[WORLD_EFFECTIVE_INTENSITY];
            d *= lightCoord.y;
            d += 0.03125;
            d = clamp(1.0 - d, 0.0, 1.0);
            diffuse = vec4(d, d, d, 1.0);
        } else {
            diffuse = vec4(v_diffuse, v_diffuse, v_diffuse, 1.0);
        }

    #elif DIFFUSE_SHADING_MODE != DIFFUSE_MODE_NONE
        vec4 diffuse = vec4(v_diffuse, v_diffuse, v_diffuse, 1.0);
    #endif

    float non_mipped = bitValue(v_flags.x, FLAG_UNMIPPED) * -4.0;
    vec4 a = texture2D(u_textures, v_texcoord, non_mipped);

    if (a.a >= 0.5 || (bitValue(v_flags.x, FLAG_CUTOUT) != 1.0)) {
        a *= colorAndLightmap(v_color, 0, light);

        #if !ENABLE_SMOOTH_LIGHT && AO_SHADING_MODE != AO_MODE_NONE && CONTEXT_IS_BLOCK
            if(bitValue(v_flags.x, FLAG_DISABLE_AO) == 0.0) {
                a *= aoFactor;
            }
        #endif

        #if DIFFUSE_SHADING_MODE != DIFFUSE_MODE_NONE
            if(bitValue(v_flags.x, FLAG_DISABLE_DIFFUSE) == 0.0) {
                a *= diffuse;
            }
        #endif
    } else {
		discard;
	}

	return a;
}

/**
 * Linear fog.  Is an inverse factor - 0 means full fog.
 */
float linearFogFactor() {
	float fogFactor = (gl_Fog.end - gl_FogFragCoord) * gl_Fog.scale;
	return clamp( fogFactor, 0.0, 1.0 );
}

/**
 * Exponential fog.  Is really an inverse factor - 0 means full fog.
 */
float expFogFactor() {
	float f = gl_FogFragCoord * gl_Fog.density;
    float fogFactor = u_fogMode == FOG_EXP ? exp(f) : exp(f * f);
    return clamp( 1.0 / fogFactor, 0.0, 1.0 );
}

/**
 * Returns either linear or exponential fog depending on current uniform value.
 */
float fogFactor() {
	return u_fogMode == FOG_LINEAR ? linearFogFactor() : expFogFactor();
}

vec4 fog(vec4 diffuseColor) {
#if CONTEXT_IS_GUI
	return diffuseColor;
#elif SUBTLE_FOG
	float f = 1.0 - fogFactor();
	f *= f;
	return mix(vec4(gl_Fog.color.rgb, diffuseColor.a), diffuseColor, 1.0 - f);
#else
	return mix(vec4(gl_Fog.color.rgb, diffuseColor.a), diffuseColor, fogFactor());
#endif
}

