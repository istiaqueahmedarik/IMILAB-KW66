"""Exercise the actual Android Store SQL schema using host SQLite (not Android runtime proof)."""
import pathlib,re,sqlite3
src=pathlib.Path('app/src/main/java/dev/kw66/glass/Store.java').read_text()
db=sqlite3.connect(':memory:')
for ddl in re.findall(r'db.execSQL\("(CREATE TABLE [^"]+)"\)',src):db.execute(ddl)
assert {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}=={'samples','packets','events','outbox','tasks'}
sample=('steps','2026-07-21T19:00',2197,0,'source-derived','{}',1)
db.execute('INSERT OR REPLACE INTO samples VALUES (?,?,?,?,?,?,?)',sample)
db.execute('INSERT OR REPLACE INTO samples VALUES (?,?,?,?,?,?,?)',sample)
assert db.execute('SELECT COUNT(*),SUM(value) FROM samples').fetchone()==(1,2197)
db.execute("INSERT INTO outbox (text,type,expires,origin) VALUES ('private',4,100,'notification')")
db.execute("INSERT INTO outbox (text,type,expires,origin) VALUES ('alarm',4,300,'alarm')")
db.execute('DELETE FROM outbox WHERE expires < ?',('200',))
assert db.execute('SELECT text FROM outbox').fetchall()==[('alarm',)]
db.execute('DELETE FROM outbox WHERE origin=?',('notification',))
assert db.execute('SELECT COUNT(*) FROM outbox').fetchone()==(1,)
db.execute('DELETE FROM outbox WHERE origin=?',('alarm',))
assert db.execute('SELECT COUNT(*) FROM outbox').fetchone()==(0,)
db.execute("INSERT INTO outbox (text,type,expires,origin) VALUES ('ordinary',4,900000,'notification')")
db.execute("INSERT INTO outbox (text,type,expires,origin) VALUES ('live call',0,10000,'call:test')")
priority=re.search(r'rows\("(SELECT \* FROM outbox ORDER BY [^"]+)"\)',src).group(1)
assert db.execute(priority).fetchone()[1]=='live call'
db.execute('DELETE FROM outbox WHERE origin=?',('call:test',))
assert db.execute('SELECT text FROM outbox').fetchall()==[('ordinary',)]
db.execute("INSERT INTO tasks (title,due,repeat_minutes,alarm) VALUES ('Todo',100,0,0)")
assert db.execute('SELECT done FROM tasks').fetchone()==(0,)
db.execute('UPDATE tasks SET done=1 WHERE id=?',(1,))
assert not db.execute('SELECT id FROM tasks WHERE done=0').fetchall()
print('PASS host SQLite schema, replay deduplication, private outbox expiration/isolation, live-call priority/removal, task completion')
