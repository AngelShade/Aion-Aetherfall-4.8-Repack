-- Bounded JSON decoder. Server text is data and is never evaluated as Lua.
WardrobeJSON = {}
local function utf8(n)
    if n < 128 then return string.char(n) end
    if n < 2048 then return string.char(192 + math.floor(n/64),128+n%64) end
    if n < 65536 then return string.char(224+math.floor(n/4096),128+math.floor(n/64)%64,128+n%64) end
    return string.char(240+math.floor(n/262144),128+math.floor(n/4096)%64,128+math.floor(n/64)%64,128+n%64)
end
function WardrobeJSON.decode(text)
    assert(type(text)=="string" and #text<=1048576,"Invalid Wardrobe response")
    local p=1
    local function space() local _,e=text:find("^%s*",p);p=(e or p-1)+1 end
    local function str()
        assert(text:sub(p,p)=='"');p=p+1;local out={}
        while p<=#text do
            local c=text:sub(p,p);p=p+1
            if c=='"' then return table.concat(out) end
            if c=='\\' then
                c=text:sub(p,p);p=p+1
                local escapes={['"']='"',['\\']='\\',['/']='/',b='\b',f='\f',n='\n',r='\r',t='\t'}
                if c=='u' then
                    local h=text:sub(p,p+3);assert(h:match('^%x%x%x%x$'));local n=tonumber(h,16);p=p+4
                    if n>=55296 and n<=56319 then
                        assert(text:sub(p,p+1)=='\\u');p=p+2;h=text:sub(p,p+3);assert(h:match('^%x%x%x%x$'))
                        local low=tonumber(h,16);assert(low>=56320 and low<=57343);p=p+4;n=65536+(n-55296)*1024+low-56320
                    else assert(n<56320 or n>57343) end
                    out[#out+1]=utf8(n)
                else assert(escapes[c]);out[#out+1]=escapes[c] end
            else assert(c:byte()>=32);out[#out+1]=c end
        end
        error('Unterminated JSON string')
    end
    local value
    value=function(depth)
        assert(depth<32);space();local c=text:sub(p,p)
        if c=='"' then return str() end
        if c=='{' or c=='[' then
            local object=c=='{';local close=object and '}' or ']';p=p+1;space();local out={}
            if text:sub(p,p)==close then p=p+1;return out end
            repeat
                space();local key=#out+1
                if object then key=str();space();assert(text:sub(p,p)==':');p=p+1 end
                out[key]=value(depth+1);space();c=text:sub(p,p);p=p+1
                if c==close then return out end
                assert(c==',')
            until false
        end
        for word,v in pairs({['true']=true,['false']=false}) do if text:sub(p,p+#word-1)==word then p=p+#word;return v end end
        if text:sub(p,p+3)=='null' then p=p+4;return nil end
        local n=text:match('^-?%d+%.?%d*[eE]?[+-]?%d*',p);assert(n and tonumber(n));p=p+#n;return tonumber(n)
    end
    local out=value(0);space();assert(p>#text,'Trailing JSON data');return out
end
function WardrobeJSON.quote(text)
    return '"'..tostring(text):gsub('[%z\1-\31\\"]',function(c)
        if c=='"' or c=='\\' then return '\\'..c end
        return string.format('\\u%04x',c:byte())
    end)..'"'
end
