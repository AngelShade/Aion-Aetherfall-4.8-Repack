// Contrast-limited detail restoration after SMAA; no depth access or invented geometry.
#include "ReShade.fxh"
uniform float DetailStrength < ui_type = "slider"; ui_min = 0.0; ui_max = 0.6; ui_label = "Fine surface detail"; > = 0.32;

float4 Detail(float4 position : SV_Position, float2 uv : TEXCOORD) : SV_Target
{
    float2 d = ReShade::PixelSize;
    float4 center = tex2D(ReShade::BackBuffer,uv);
    float3 north = tex2D(ReShade::BackBuffer,uv - float2(0,d.y)).rgb;
    float3 south = tex2D(ReShade::BackBuffer,uv + float2(0,d.y)).rgb;
    float3 east = tex2D(ReShade::BackBuffer,uv + float2(d.x,0)).rgb;
    float3 west = tex2D(ReShade::BackBuffer,uv - float2(d.x,0)).rgb;
    float3 lo = min(center.rgb,min(min(north,south),min(east,west)));
    float3 hi = max(center.rgb,max(max(north,south),max(east,west)));
    float contrast = max(hi.r-lo.r,max(hi.g-lo.g,hi.b-lo.b));
    // Strong silhouette and glyph edges receive no enhancement; retain AA and avoid halos.
    float weight = (1.0-smoothstep(0.08,0.28,contrast))*smoothstep(0.003,0.035,contrast);
    float3 residual = center.rgb - (north+south+east+west)*0.25;
    float3 color = clamp(center.rgb + residual*DetailStrength*weight,lo,hi);
    return float4(color,center.a);
}

technique Aion_FineDetail
{
    pass { VertexShader = PostProcessVS; PixelShader = Detail; }
}
