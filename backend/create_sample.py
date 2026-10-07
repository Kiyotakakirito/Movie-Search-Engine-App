from pathlib import Path
import sqlite3
import struct
import zlib

ROOT = Path(__file__).parent
DATA = ROOT / 'data'
DATA.mkdir(exist_ok=True)
POSTERS = ROOT / 'sample_posters'
POSTERS.mkdir(exist_ok=True)
db_path = DATA / 'sample.db'
db_path.unlink(missing_ok=True)
db = sqlite3.connect(db_path)
db.executescript('''
    CREATE TABLE movies(id TEXT PRIMARY KEY, title TEXT, original TEXT, search_title TEXT, search_original TEXT, year INTEGER, runtime INTEGER, genres TEXT, rating REAL, votes INTEGER);
    CREATE TABLE credits(id TEXT, person TEXT, category TEXT, position INTEGER);
    CREATE TABLE names(id TEXT PRIMARY KEY, name TEXT);
''')
titles = ['The Last Orbit', 'Orbit: A New Dawn', 'Beyond the Orbit', 'Orbit of Dreams', 'Silent Orbit', 'Orbit: Homecoming']
colors = [(20, 54, 72), (85, 41, 47), (34, 61, 50), (58, 45, 80), (80, 62, 32), (37, 58, 81)]

def chunk(kind, body):
    return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body))

for i, title in enumerate(titles):
    identifier = f'sample{i + 1}'
    db.execute('INSERT INTO movies VALUES(?,?,?,?,?,?,?,?,?,?)', (identifier, title, title, title.casefold(), title.casefold(), 2018 + i, 100 + i * 7, 'Adventure,Sci-Fi', round(7.1 + i / 10, 1), 1800 + i * 350))
    db.execute('INSERT OR IGNORE INTO names VALUES(?,?)', ('director', 'Alex Taylor (sample)'))
    db.execute('INSERT OR IGNORE INTO names VALUES(?,?)', ('actor', 'Sam Lee (sample)'))
    db.executemany('INSERT INTO credits VALUES(?,?,?,?)', [(identifier, 'director', 'director', 0), (identifier, 'actor', 'cast', 1)])
    pixels = bytearray()
    for y in range(420):
        pixels.append(0)
        for x in range(280):
            color = colors[i]
            distance = ((x - 140) ** 2 + (y - 165) ** 2) ** 0.5
            if distance < 53:
                color = (214, 191, 140)
            if 85 < distance < 88:
                color = (146, 167, 160)
            if (x * 73 + y * 17) % 1999 == 0 and y < 290:
                color = (232, 233, 210)
            if 325 < y < 331 and 50 < x < 230:
                color = (214, 191, 140)
            if 352 < y < 356 and 82 < x < 198:
                color = (146, 167, 160)
            pixels.extend(color)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 280, 420, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')
    (POSTERS / f'{identifier}.png').write_bytes(png)
db.commit()
db.close()
print('Created 6 fictional sample movies. Start server.py --demo to use them.')
