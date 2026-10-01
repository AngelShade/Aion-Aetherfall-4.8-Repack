// Aion-wide color response and restrained light bloom. No depth buffer required.
#include "ReShade.fxh"
uniform float BloomStrength < ui_type = "slider"; ui_min = 0.0; ui_max = 0.3; ui_label = "Light bloom"; > = 0.14;
uniform float BloomThreshold < ui_type = "slider"; ui_min = 0.5; ui_max = 1.0; > = 0.72;
uniform float Contrast < ui_type = "slider"; ui_min = 0.0; ui_max = 0.3; > = 0.10;
uniform float Saturation < ui_type = "slider"; ui_min = 0.8; ui_max = 1.2; > = 1.025;

texture BloomA { Width = BUFFER_WIDTH / 4; Height = BUFFER_HEIGHT / 4; Format = RGBA16F; };
texture BloomB { Width = BUFFER_WIDTH / 4; Height = BUFFER_HEIGHT / 4; Format = RGBA16F; };
sampler BloomAS { Texture = BloomA; AddressU = CLAMP; AddressV = CLAMP; MinFilter = LINEAR; MagFilter = LINEAR; };
sampler BloomBS { Texture = BloomB; AddressU = CLAMP; AddressV = CLAMP; MinFilter = LINEAR; MagFilter = LINEAR; };

float3 Bright(float2 uv)
{
    float3 c = tex2D(ReShade::BackBuffer, uv).rgb;
    float high = max(c.r, max(c.g, c.b));
    float low = min(c.r, min(c.g, c.b));
    // Reduce glow from white UI lettering while allowing colorful magic and lights.
    float chromaWeight = lerp(0.08, 1.0, smoothstep(0.04, 0.20, high - low));
    return c * smoothstep(BloomThreshold, 1.0, high) * chromaWeight;
}
float4 Extract(float4 p : SV_Position, float2 uv : TEXCOORD) : SV_Target
{
    float2 d = ReShade::PixelSize;
    float3 c = Bright(uv) * 0.2;
    c += (Bright(uv + d * float2(1,1)) + Bright(uv + d * float2(-1,1))
        + Bright(uv + d * float2(1,-1)) + Bright(uv - d)) * 0.2;
    return float4(c, 1.0);
}
float4 Horizontal(float4 p : SV_Position, float2 uv : TEXCOORD) : SV_Target
{
    float2 d = float2(4.0 * BUFFER_RCP_WIDTH, 0);
    float3 c = tex2D(BloomAS, uv).rgb * 0.227027;
    c += (tex2D(BloomAS, uv + d * 1.384615).rgb + tex2D(BloomAS, uv - d * 1.384615).rgb) * 0.316216;
    c += (tex2D(BloomAS, uv + d * 3.230769).rgb + tex2D(BloomAS, uv - d * 3.230769).rgb) * 0.070270;
    return float4(c, 1.0);
}
float4 Vertical(float4 p : SV_Position, float2 uv : TEXCOORD) : SV_Target
{
    float2 d = float2(0, 4.0 * BUFFER_RCP_HEIGHT);
    float3 c = tex2D(BloomBS, uv).rgb * 0.227027;
    c += (tex2D(BloomBS, uv + d * 1.384615).rgb + tex2D(BloomBS, uv - d * 1.384615).rgb) * 0.316216;
    c += (tex2D(BloomBS, uv + d * 3.230769).rgb + tex2D(BloomBS, uv - d * 3.230769).rgb) * 0.070270;
    return float4(c, 1.0);
}
float4 Composite(float4 p : SV_Position, float2 uv : TEXCOORD) : SV_Target
{
    float4 source = tex2D(ReShade::BackBuffer, uv);
    float3 c = saturate(source.rgb);
    float luma = dot(c, float3(0.2126, 0.7152, 0.0722));
    float chroma = max(c.r,max(c.g,c.b)) - min(c.r,min(c.g,c.b));
    float worldWeight = smoothstep(0.02, 0.12, chroma);
    // Preserve black and white endpoints; keep monochrome UI largely unchanged.
    float shaped = luma + Contrast * worldWeight * (luma - 0.5) * luma * (1.0 - luma) * 4.0;
    c *= shaped / max(luma, 0.0001);
    c = lerp(shaped.xxx, c, lerp(1.0, Saturation, worldWeight));
    // Screen blend keeps highlights from clipping to solid white.
    c = 1.0 - (1.0 - saturate(c)) * (1.0 - saturate(tex2D(BloomAS,uv).rgb * BloomStrength));
    return float4(c,source.a);
}
technique Aion_LightAndColor
{
    pass { VertexShader = PostProcessVS; PixelShader = Extract; RenderTarget = BloomA; }
    pass { VertexShader = PostProcessVS; PixelShader = Horizontal; RenderTarget = BloomB; }
    pass { VertexShader = PostProcessVS; PixelShader = Vertical; RenderTarget = BloomA; }
    pass { VertexShader = PostProcessVS; PixelShader = Composite; }
}
