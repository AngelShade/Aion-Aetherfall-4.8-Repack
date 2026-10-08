#pragma once
// Pure scheduling state: no Windows, game, widget or credential access.
struct RememberLifecycle {
    void* pending=nullptr;
    void arm(void* dialog){if(dialog)pending=dialog;}
    void reset(void* singleton,void* dialog){if(singleton && singleton==dialog)arm(dialog);}
    bool consume(void* dialog,bool visible,bool widgetsReady){
        if(!dialog || pending!=dialog || !visible || !widgetsReady)return false;
        pending=nullptr;return true;
    }
};
