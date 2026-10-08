"""Small typed NBT reader/writer used to downgrade the selected structure templates."""
import io,gzip,struct
def read(data):
    f=io.BytesIO(gzip.decompress(data) if data[:2]==b'\x1f\x8b' else data)
    def num(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize(fmt)))[0]
    def string():return f.read(num('H')).decode('utf-8')
    def value(t):
        if t in range(1,7):return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==7:return f.read(num('i'))
        if t==8:return string()
        if t==9:
            subtype=num('B');return (subtype,[value(subtype) for _ in range(num('i'))])
        if t==10:
            d={}
            while (subtype:=num('B')):
                key=string();d[key]=(subtype,value(subtype))
            return d
        if t in (11,12):return [num('i' if t==11 else 'q') for _ in range(num('i'))]
        raise ValueError(t)
    t=num('B');name=string();return name,value(t)
def write(name,root):
    f=io.BytesIO()
    def num(fmt,v):f.write(struct.pack('>'+fmt,v))
    def string(v):b=v.encode('utf-8');num('H',len(b));f.write(b)
    def value(t,v):
        if t in range(1,7):num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
        elif t==7:num('i',len(v));f.write(v)
        elif t==8:string(v)
        elif t==9:
            subtype,items=v;num('B',subtype);num('i',len(items))
            for x in items:value(subtype,x)
        elif t==10:
            for k,(subtype,x) in v.items():num('B',subtype);string(k);value(subtype,x)
            num('B',0)
        elif t in (11,12):
            num('i',len(v))
            for x in v:num('i' if t==11 else 'q',x)
    num('B',10);string(name);value(10,root);return gzip.compress(f.getvalue(),mtime=0)
def plain(v):
    if isinstance(v,dict):return {k:plain(x[1]) for k,x in v.items()}
    if isinstance(v,tuple) and len(v)==2 and isinstance(v[0],int):return [plain(x) for x in v[1]]
    return v
