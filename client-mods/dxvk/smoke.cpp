// Hardware validation of the downloaded renderer, without restarting Aion.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <d3d9.h>
#include <cstdio>

static void check(HRESULT result, const char* operation) {
    if (FAILED(result)) {
        std::fprintf(stderr, "%s failed: 0x%08lx\n", operation, (unsigned long)result);
        ExitProcess(1);
    }
}

int wmain(int argc, wchar_t** argv) {
    if (argc != 4) return 2;
    SetEnvironmentVariableW(L"DXVK_CONFIG_FILE", argv[2]);
    SetEnvironmentVariableW(L"DXVK_LOG_PATH", argv[3]);
    HMODULE dll = LoadLibraryW(argv[1]);
    if (!dll) { std::fprintf(stderr, "LoadLibrary failed: %lu\n", GetLastError()); return 1; }
    auto create = reinterpret_cast<IDirect3D9* (WINAPI*)(UINT)>(GetProcAddress(dll, "Direct3DCreate9"));
    if (!create) return 1;
    IDirect3D9* d3d = create(D3D_SDK_VERSION);
    if (!d3d) return 1;
    D3DADAPTER_IDENTIFIER9 adapter{};
    check(d3d->GetAdapterIdentifier(0, 0, &adapter), "GetAdapterIdentifier");
    std::printf("Adapter: %s\n", adapter.Description);
    WNDCLASSW wc{}; wc.lpfnWndProc = DefWindowProcW;
    wc.hInstance = GetModuleHandleW(nullptr); wc.lpszClassName = L"AionDXVKHiddenCheck";
    RegisterClassW(&wc);
    // The window is never shown; no visible game/test window is opened.
    HWND window = CreateWindowW(wc.lpszClassName, L"DXVK check", WS_OVERLAPPEDWINDOW,
                                0, 0, 128, 128, nullptr, nullptr, wc.hInstance, nullptr);
    if (!window) return 1;
    D3DPRESENT_PARAMETERS pp{}; pp.Windowed = TRUE; pp.hDeviceWindow = window;
    pp.BackBufferWidth = 64; pp.BackBufferHeight = 64;
    pp.BackBufferFormat = D3DFMT_UNKNOWN; pp.SwapEffect = D3DSWAPEFFECT_DISCARD;
    pp.PresentationInterval = D3DPRESENT_INTERVAL_IMMEDIATE;
    IDirect3DDevice9* device = nullptr;
    check(d3d->CreateDevice(0, D3DDEVTYPE_HAL, window,
        D3DCREATE_HARDWARE_VERTEXPROCESSING, &pp, &device), "CreateDevice");
    D3DCAPS9 caps{}; check(device->GetDeviceCaps(&caps), "GetDeviceCaps");
    if (caps.MaxAnisotropy < 16) return 1;
    IDirect3DSurface9 *target = nullptr, *readback = nullptr, *backbuffer = nullptr;
    check(device->GetRenderTarget(0, &backbuffer), "GetRenderTarget");
    check(device->CreateRenderTarget(64,64,D3DFMT_A8R8G8B8,D3DMULTISAMPLE_NONE,0,FALSE,&target,nullptr), "CreateRenderTarget");
    check(device->CreateOffscreenPlainSurface(64,64,D3DFMT_A8R8G8B8,D3DPOOL_SYSTEMMEM,&readback,nullptr), "CreateReadback");
    check(device->SetRenderTarget(0,target), "SetRenderTarget");
    check(device->Clear(0,nullptr,D3DCLEAR_TARGET,0xFF2A5678,1.0f,0), "Clear");
    struct Vertex { float x,y,z,rhw; DWORD color; };
    Vertex vertices[] = {{8,8,0,1,0xFF44BB66},{56,8,0,1,0xFF44BB66},{8,56,0,1,0xFF44BB66}};
    check(device->SetRenderState(D3DRS_CULLMODE,D3DCULL_NONE), "DisableCulling");
    check(device->SetRenderState(D3DRS_LIGHTING,FALSE), "DisableLighting");
    check(device->SetFVF(D3DFVF_XYZRHW|D3DFVF_DIFFUSE), "SetFVF");
    check(device->BeginScene(), "BeginScene");
    check(device->DrawPrimitiveUP(D3DPT_TRIANGLELIST,1,vertices,sizeof(Vertex)), "DrawTriangle");
    check(device->EndScene(), "EndScene");
    check(device->GetRenderTargetData(target,readback), "GPUReadback");
    D3DLOCKED_RECT pixels{}; check(readback->LockRect(&pixels,nullptr,D3DLOCK_READONLY), "LockReadback");
    auto sample = [&](int x,int y) { return *reinterpret_cast<DWORD*>(static_cast<unsigned char*>(pixels.pBits)+y*pixels.Pitch+x*4); };
    const DWORD inside = sample(16,16), outside = sample(60,60);
    check(readback->UnlockRect(), "UnlockReadback");
    if (inside != 0xFF44BB66 || outside != 0xFF2A5678) {
        std::fprintf(stderr,"Pixel mismatch: %08lx %08lx\n",inside,outside); return 1;
    }
    check(device->SetRenderTarget(0,backbuffer), "RestoreBackbuffer");
    check(device->Clear(0,nullptr,D3DCLEAR_TARGET,0xFF2A5678,1.0f,0), "ClearBackbuffer");
    check(device->Present(nullptr,nullptr,nullptr,nullptr), "Present");
    readback->Release(); target->Release(); backbuffer->Release();
    device->Release(); d3d->Release(); DestroyWindow(window); FreeLibrary(dll);
    std::puts("PASS: native DXVK device, shader draw, exact GPU pixel readback, presentation and 16x filtering support.");
    return 0;
}
