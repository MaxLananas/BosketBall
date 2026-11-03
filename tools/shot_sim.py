import math, random

g, drag = 0.046, 0.991
RIM_R, TUBE, BR = 0.45, 0.042, 0.285
SCORE = 0.39
OFF, HW, MINY, MAXY = 0.650, 0.92, -0.15, 1.15
MAXYAW = 22.0

def aim(eye, look, rim):
    d = math.sqrt(sum(c*c for c in look))
    look = [c/d for c in look]
    dx, dz = rim[0]-eye[0], rim[2]-eye[2]
    hd = math.hypot(dx, dz)
    trh = (dx/hd, dz/hd)
    lh = (look[0], look[2]); n=math.hypot(*lh); lh=(lh[0]/n, lh[1]/n)
    right=(-trh[1], trh[0])
    siny = lh[0]*right[0]+lh[1]*right[1]
    cosy = max(-1,min(1, lh[0]*trh[0]+lh[1]*trh[1]))
    yaw = math.degrees(math.atan2(siny,cosy))
    pitch = math.degrees(-math.asin(max(-1,min(1,look[1]))))
    ideal = -min(34.0, 9.5+hd*1.35)
    ok = abs(yaw)<=MAXYAW and -68.0<=pitch<=22.0 and hd<=34.0
    return ok, yaw, pitch-ideal, hd

def arc(start, target, flight, grav=g, dr=drag):
    sub = int(round(flight*4)); dt=0.25; sd = dr**dt
    sumd=0.0; gv=0.0; gy=0.0; cf=1.0
    for _ in range(sub):
        gv=(gv-grav*dt)*sd; cf*=sd; sumd+=cf*dt; gy+=gv*dt
    return [(target[0]-start[0])/sumd, (target[1]-start[1]-gy)/sumd, (target[2]-start[2])/sumd]

def solve(eye, look, rim, quality_green, charge, contest, rng):
    ok, yaw, perr, dist = aim(eye, look, rim)
    if not ok:
        s = 0.56 + max(0.15,min(1.20,charge))*0.92
        d=math.sqrt(sum(c*c for c in look))
        return [look[0]/d*s, look[1]/d*s+0.11, look[2]/d*s], False
    dx, dz = rim[0]-eye[0], rim[2]-eye[2]
    tr = (dx/dist, dz/dist)
    right=(-tr[1], tr[0])
    forg = 0.56 if (quality_green and contest<0.30) else 0.84
    lat = dist*math.tan(math.radians(yaw*forg)); lat=max(-2.6,min(2.6,lat))
    lo = max(0.80, 0.845 + max(0.0, dist-10.0)*0.003); hi = 1.025
    if lo<=charge<=hi: raw=0.0
    elif charge<lo: raw=charge-lo
    else: raw=charge-hi
    depth = raw*0.14 if (quality_green and contest<0.35) else raw*(2.05+min(0.80,dist*0.05))
    if abs(perr)>12.0:
        ex = perr - math.copysign(12.0, perr)
        depth -= ex*0.028
    if quality_green:
        pressure = max(0.0, dist-5.0)*0.075 + contest*0.45
        if pressure>0:
            lat += (rng.random()+rng.random()-1.0)*pressure
            depth += (rng.random()+rng.random()-1.0)*pressure*0.7
    if contest>0.08:
        js = contest*(0.14 if quality_green else 0.34)
        lat += (rng.random()-0.5)*2*js; depth += (rng.random()-0.5)*2*js
    target=[rim[0]+right[0]*lat+tr[0]*depth, rim[1]+0.06, rim[2]+right[1]*lat+tr[1]*depth]
    flight = max(15.0,min(44.0, 13.5+dist*1.48))
    if perr < -8.0: flight=min(48.0, flight+min(6.0,(-perr-8.0)*0.25))
    return arc(eye,target,flight), True

def rimcol(p,v,rim,rest=0.56):
    dx,dy,dz = p[0]-rim[0], p[1]-rim[1], p[2]-rim[2]
    hd=math.hypot(dx,dz)
    if hd>1e-5: cx,cz = dx/hd*RIM_R, dz/hd*RIM_R
    else: cx,cz = RIM_R,0.0
    nx,ny,nz = dx-cx, dy, dz-cz
    dt=math.sqrt(nx*nx+ny*ny+nz*nz); ms=BR+TUBE
    if dt>=ms or dt<1e-6: return p,v,False
    n=(nx/dt,ny/dt,nz/dt)
    push=[p[0]+n[0]*(ms-dt),p[1]+n[1]*(ms-dt),p[2]+n[2]*(ms-dt)]
    vn=v[0]*n[0]+v[1]*n[1]+v[2]*n[2]
    if vn>=0: return push,v,False
    r=[v[i]-n[i]*(1+rest)*vn for i in range(3)]
    r[0]*=0.94; r[2]*=0.94
    return push, r, True

def boardcol(p,v,rim,fwd,rest=0.66):
    fn=math.hypot(fwd[0],fwd[2]); fwd=(fwd[0]/fn,0.0,fwd[2]/fn)
    right=(-fwd[2],0.0,fwd[0])
    rel=[p[i]-rim[i] for i in range(3)]
    rr=rel[0]*right[0]+rel[2]*right[2]
    if abs(rr)>HW+BR*0.5: return p,v,False
    if rel[1]<MINY or rel[1]>MAXY: return p,v,False
    rf=rel[0]*fwd[0]+rel[2]*fwd[2]; vf=v[0]*fwd[0]+v[2]*fwd[2]
    if vf<0.0 and (rf-BR)<= -OFF and rf>=-OFF-0.42:
        pd=(-OFF+BR)-rf
        pushed=[p[0]+fwd[0]*pd,p[1],p[2]+fwd[2]*pd]
        vr=(v[0]*right[0]+v[2]*right[2])*0.92
        vy=v[1]*0.92
        nvf=-vf*rest
        return pushed,[fwd[0]*nvf+right[0]*vr, vy, fwd[2]*nvf+right[2]*vr],True
    return p,v,False

def scored(prev,cur,rim):
    if prev[1]<rim[1] or cur[1]>=rim[1]: return False
    den=prev[1]-cur[1]; t=(prev[1]-rim[1])/den if den>1e-6 else 0.5
    cx=prev[0]+(cur[0]-prev[0])*t; cz=prev[2]+(cur[2]-prev[2])*t
    return math.hypot(cx-rim[0],cz-rim[2])<=SCORE

def simulate(eye, vel, rim, fwd, floorY=64.0):
    p=list(eye); v=list(vel); dt=0.25; sd=drag**dt
    for t in range(70):
        for s in range(4):
            prev=list(p)
            v[1]-=g*dt
            for i in range(3): v[i]*=sd
            p=[p[i]+v[i]*dt for i in range(3)]
            p,v,hit = rimcol(p,v,rim)
            p,v,_ = boardcol(p,v,rim,fwd)
            if scored(prev,p,rim): return 'SCORE'
            if p[1]<=floorY+BR: return 'FLOOR'
    return 'FLIGHT'

rim=(0.5,67.05,0.5+13.0)
fwd=(0.0,0.0,-1.0)
def eye_at(dist):
    return (0.5, 65.62, rim[2]-dist)
def look_at(eye, rim, extra_pitch=0.0):
    dx,dz = rim[0]-eye[0], rim[2]-eye[2]
    d=math.hypot(dx,dz)
    pitchDeg = -9.5 - min(34.0 - 9.5, d * 1.35) + extra_pitch
    p = -math.radians(pitchDeg)
    return (dx / d * math.cos(p), math.sin(p), dz / d * math.cos(p))

rng=random.Random(7)
for dist in (2.5, 4.0, 7.8, 12.0):
    eye=eye_at(dist)
    look=look_at(eye,rim)
    for label,charge,green in (("green",0.95,True),("slightly_early",0.70,False),("slightly_late",1.06,False),("very_early",0.40,False)):
        res={}
        for n in range(120):
            v,ok=solve(eye,look,rim,green,charge,0.0,rng)
            r=simulate(eye,v,rim,fwd)
            res[r]=res.get(r,0)+1
        print(f"dist {dist:5.1f} {label:15s} ok={ok} {res}")

print("---- trace dist 7.8 green ----")
eye=eye_at(7.8); look=look_at(eye,rim)
v,ok=solve(eye,look,rim,True,0.95,0.0,rng)
print("vel",[round(c,4) for c in v])
p=list(eye); vv=list(v); dt=0.25; sd=drag**dt
for t in range(60):
    for s in range(4):
        vv[1]-=g*dt
        for i in range(3): vv[i]*=sd
        p=[p[i]+vv[i]*dt for i in range(3)]
    if t%4==0: print(t, [round(c,3) for c in p])
    if p[1]<=64.285: print("floor at tick",t,p); break
