"""Recompute Table III (Breaking_updates.csv) from Transfer_targets.csv and check that they agree.
Per BU: targets, fixed clients, fix rate (integer %, half-up from the counts, and the 1-decimal column), test-baseline counts (before / after / both) and tests star (>=1 fixed client whose tests pass both before the update and after the transformation). Totals and mean.
Usage: python3 check_final.py   (run in this folder; exit code 0 = all checks pass)"""
import csv,collections,statistics as st,sys,os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
T=list(csv.DictReader(open('Transfer_targets.csv'))); B=list(csv.DictReader(open('Breaking_updates.csv')))
rows=[b for b in B if b['BU id'].startswith('BU')]; tot=next(b for b in B if b['BU id']=='Total'); mean=next(b for b in B if b['BU id']=='Mean per transformation')
g=collections.defaultdict(list)
for t in T: g[t['BU id']].append(t)
bad=[]
for b in rows:
    ts=g.get(b['BU id'],[]); n=len(ts); f=sum(t['transfer result']=='FIXED' for t in ts)
    for k,exp,got in (('targets',int(b['reproduced clients']),n),('fixed',int(b['fixed clients']),f),('rate % (integer, half-up, from counts)',int(100*int(b['fixed clients'])/int(b['reproduced clients'])+0.5),int(100*f/n+0.5)),('rate % (1 decimal column)',float(b['fix rate (%)']),round(100*f/n,1)),
                      ('tests pass before',int(b['fixed: tests pass before update']),sum(t['tests before update']=='PASS' for t in ts if t['transfer result']=='FIXED')),
                      ('tests pass after',int(b['fixed: tests pass after transformation']),sum(t['tests after transformation']=='PASS' for t in ts if t['transfer result']=='FIXED')),
                      ('tests pass both',int(b['fixed: tests pass before and after']),sum(t['tests before update']=='PASS' and t['tests after transformation']=='PASS' for t in ts if t['transfer result']=='FIXED')),
                      ('tests star',b['tests star'],'*' if any(t['tests before update']=='PASS' and t['tests after transformation']=='PASS' for t in ts if t['transfer result']=='FIXED') else '')):
        if exp!=got: bad.append((b['BU id'],k,exp,got))
N=len(T); F=sum(t['transfer result']=='FIXED' for t in T)
m=round(st.mean(100*sum(t['transfer result']=='FIXED' for t in g[b['BU id']])/len(g[b['BU id']]) for b in rows),1)
checks=[('BUs',24,len(rows)),('BUs in Transfer_targets',24,len(g)),('targets',558,N),('fixed',250,F),('pooled %',44.8,round(100*F/N,1)),('mean %',60.5,m),
        ('Total row targets',N,int(tot['reproduced clients'])),('Total row fixed',F,int(tot['fixed clients'])),('Total row rate',round(100*F/N,1),float(tot['fix rate (%)'])),
        ('Mean row',m,float(mean['fix rate (%)'])),
        ('non-FIXED have NA test classes',True,all(t['tests before update']=='NA' and t['tests after transformation']=='NA' for t in T if t['transfer result']!='FIXED')),
        ('Total tests pass before',sum(int(b['fixed: tests pass before update']) for b in rows),int(tot['fixed: tests pass before update'])),
        ('Total tests pass both',sum(int(b['fixed: tests pass before and after']) for b in rows),int(tot['fixed: tests pass before and after']))]
bad+= [('TOTAL',k,e,gt) for k,e,gt in checks if e!=gt]
for c in checks: print(f'{c[0]:28s} expected {c[1]!s:>6}  got {c[2]!s:>6}  {"OK" if c[1]==c[2] else "MISMATCH"}')
print('per-BU checks:',len(rows),'BUs x 8 fields,', 'all OK' if not [x for x in bad if x[0]!='TOTAL'] else 'MISMATCHES'); [print('  MISMATCH',x) for x in bad]
sys.exit(1 if bad else 0)
