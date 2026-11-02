// The plugin's slash commands as one immutable list (included by slash_lookup.h).
// Lua's addon worker publishes it; the game thread reads it without entering Lua.
#pragma once
#include <algorithm>
#include <atomic>
#include <cstring>
#include <string>
#include <vector>

namespace slash_names {
struct List {std::string published;std::vector<std::string> names;};
inline std::atomic<const List*> current{nullptr};
inline char lower(char c){return c>='A' && c<='Z'?char(c+32):c;}
inline bool space(char c){return c==' ' || (c>='\t' && c<='\r');}
// The stock pattern "^/[.%S]+", lower-cased into out; false when the line is not a command.
inline bool command_of(const char* line,char* out,size_t capacity){
    if(!line || line[0]!='/' || !line[1] || space(line[1]))return false;
    size_t n=0;
    for(;line[n] && !space(line[n]);++n){
        if(n+1>=capacity)return false;
        out[n]=lower(line[n]);
    }
    out[n]=0;return true;
}
inline bool known(const List& list,const char* line){
    char command[128];
    if(!command_of(line,command,sizeof command))return false;
    return std::binary_search(list.names.begin(),list.names.end(),std::string(command));
}
// Newline-separated names. A replaced list stays allocated because the game thread
// may still be reading it; a new one only appears when a command is added.
inline bool publish(const char* text,size_t length){
    if(!text || !length)return false;
    const List* previous=current.load(std::memory_order_acquire);
    if(previous && previous->published.size()==length && !std::memcmp(previous->published.data(),text,length))return false;
    try{
        auto next=new List;
        next->published.assign(text,length);
        for(size_t start=0;start<=length;){
            const char* end=static_cast<const char*>(std::memchr(text+start,'\n',length-start));
            size_t stop=end?size_t(end-text):length;
            if(stop>start){
                std::string name(text+start,stop-start);
                std::transform(name.begin(),name.end(),name.begin(),lower);
                next->names.push_back(std::move(name));
            }
            start=stop+1;
        }
        std::sort(next->names.begin(),next->names.end());
        current.store(next,std::memory_order_release);
        return true;
    }catch(...){return false;}
}
}
