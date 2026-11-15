#pragma once

#include <windows.h>
#include <map>
#include <string>
#include <string_view>
#include <utility>

namespace mod_text {

// The launcher exposes only the composed catalog for this game process.
inline std::string read_catalog(){
    constexpr DWORD max_bytes=1024*1024;
    wchar_t path[32768];
    DWORD length=GetEnvironmentVariableW(L"AETHERFALL_MOD_TEXT",path,DWORD(sizeof(path)/sizeof(*path)));
    if(!length || length>=sizeof(path)/sizeof(*path))return {};
    HANDLE file=CreateFileW(path,GENERIC_READ,FILE_SHARE_READ,nullptr,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,nullptr);
    if(file==INVALID_HANDLE_VALUE)return {};
    LARGE_INTEGER size{};
    std::string data;
    if(GetFileSizeEx(file,&size) && size.QuadPart>0 && size.QuadPart<=max_bytes){
        data.resize(size_t(size.QuadPart));
        DWORD read=0;
        if(!ReadFile(file,data.data(),DWORD(data.size()),&read,nullptr) || read!=data.size())data.clear();
    }
    CloseHandle(file);
    return data;
}

inline int hex_digit(char value){
    if(value>='0' && value<='9')return value-'0';
    if(value>='A' && value<='F')return value-'A'+10;
    if(value>='a' && value<='f')return value-'a'+10;
    return -1;
}

inline std::wstring decode_field(std::string_view field){
    std::string utf8;
    for(size_t i=0;i<field.size();++i){
        if(field[i]=='%' && i+2<field.size()){
            int first=hex_digit(field[i+1]),second=hex_digit(field[i+2]);
            if(first>=0 && second>=0){
                utf8.push_back(char(first*16+second));
                i+=2;
                continue;
            }
        }
        utf8.push_back(field[i]);
    }
    if(utf8.empty() || utf8.find('\0')!=std::string::npos)return {};
    int size=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,utf8.data(),int(utf8.size()),nullptr,0);
    if(!size)return {};
    std::wstring wide(size,L'\0');
    if(!MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,utf8.data(),int(utf8.size()),wide.data(),size))return {};
    return wide;
}

inline auto read_labels(){
    std::map<std::wstring,std::wstring,std::less<>> labels;
    const auto data=read_catalog();
    std::string_view rows=data;
    constexpr std::string_view header="AFTEXT1\n";
    if(rows.substr(0,header.size())!=header)return labels;
    rows.remove_prefix(header.size());
    for(size_t end=rows.find('\n');end!=rows.npos;end=rows.find('\n')){
        auto row=rows.substr(0,end);
        rows.remove_prefix(end+1);
        size_t tab=row.find('\t');
        if(tab==row.npos)continue;
        auto source=decode_field(row.substr(0,tab));
        auto target=decode_field(row.substr(tab+1));
        if(!source.empty() && !target.empty())labels.insert_or_assign(std::move(source),std::move(target));
    }
    return labels;
}

inline const wchar_t* label(const wchar_t* source,const wchar_t* key=nullptr){
    if(!*source)return source;
    static const auto labels=read_labels();
    const auto value=labels.find(key?key:source);
    return value==labels.end()?source:value->second.c_str();
}

}
