// Offline checks for slash_names.h against the stock IsSlashCmd rules.
// cl /nologo /std:c++17 /EHsc /W4 /WX verify_slash_names.cpp && verify_slash_names.exe
#include <cstdio>
#include "slash_names.h"
using namespace slash_names;
int failures=0;
void check(bool value,const char* label){if(!value){++failures;std::printf("FAILED: %s\n",label);}}
int main(){
    const char list[]="/Companions\n/afui\n/seasonpass\n/.odd\n/Wardrobe";
    check(publish(list,sizeof list-1),"first list publishes");
    const List* first=current.load();
    check(first && first->names.size()==5,"every name kept");
    check(!publish(list,sizeof list-1),"an unchanged list is not replaced");
    check(current.load()==first,"the same list stays current");
    check(known(*first,"/companions"),"exact command");
    check(known(*first,"/COMPANIONS"),"line case ignored");
    check(known(*first,"/wardrobe"),"list case ignored");
    check(known(*first,"/seasonpass and arguments"),"arguments ignored");
    check(known(*first,"/afui\tx"),"tab ends the command");
    check(known(*first,"/.odd"),"dot in a command");
    check(!known(*first,"/say hello"),"native command falls through");
    check(!known(*first,"/companion"),"prefix does not match");
    check(!known(*first,"/companionsx"),"longer word does not match");
    check(!known(*first,"companions"),"line without a slash");
    check(!known(*first," /companions"),"pattern is anchored");
    check(!known(*first,"/"),"slash alone");
    check(!known(*first,"/ companions"),"space after the slash");
    check(!known(*first,""),"empty line");
    check(!known(*first,nullptr),"null line");
    std::string longest(500,'a');longest="/"+longest;
    check(!known(*first,longest.c_str()),"over-long command");
    check(!publish(nullptr,0),"nothing to publish");
    check(!publish("",0),"empty list ignored");
    check(current.load()==first,"ignored publishes keep the list");
    const char more[]="/afui\n\n/new\n";
    check(publish(more,sizeof more-1),"a changed list publishes");
    const List* second=current.load();
    check(second!=first && second->names.size()==2,"blank lines skipped");
    check(known(*second,"/new") && !known(*second,"/wardrobe"),"new list answers");
    check(known(*first,"/wardrobe"),"a replaced list stays readable");
    std::printf(failures?"%d failed\n":"all passed\n",failures);
    return failures!=0;
}
