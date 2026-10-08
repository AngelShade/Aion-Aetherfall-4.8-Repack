#include "remember_lifecycle.h"
#include <cassert>
#include <iostream>
int main(){
    int login,other;RememberLifecycle state;
    state.arm(nullptr);assert(!state.consume(&login,true,true));
    state.arm(&login);
    assert(!state.consume(&login,false,true));
    assert(!state.consume(&login,true,false));
    assert(!state.consume(&other,true,true));
    assert(state.consume(&login,true,true));
    for(int draw=0;draw<100;++draw)assert(!state.consume(&login,true,true));
    state.reset(&login,&other);assert(!state.consume(&login,true,true));
    state.reset(nullptr,&login);assert(!state.consume(&login,true,true));
    // Reset without a visible-bit change is the reported in-game logout path.
    for(int reset=0;reset<10;++reset){
        state.reset(&login,&login);
        assert(!state.consume(&login,true,false));
        assert(state.consume(&login,true,true));
        assert(!state.consume(&login,true,true));
    }
    std::cout<<"OK: initial/hidden/reset scheduling, widget readiness, unrelated dialogs and no repeated typing overwrite\n";
}
