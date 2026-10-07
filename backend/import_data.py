import argparse
import csv
import gzip
import logging
from pathlib import Path
import sqlite3
import urllib.request
import shutil

DATA = Path(__file__).parent / 'data'
FILES = ['title.basics', 'title.ratings', 'title.crew', 'title.principals', 'name.basics']

def rows(name):
    path = DATA / f'{name}.tsv.gz'
    if not path.exists():
        logging.info('Downloading %s', name)
        temporary = path.with_suffix('.part')
        with urllib.request.urlopen(f'https://datasets.imdbws.com/{name}.tsv.gz', timeout=60) as response, temporary.open('wb') as output:
            shutil.copyfileobj(response, output)
        temporary.replace(path)
    with gzip.open(path, 'rt', encoding='utf-8') as source:
        yield from csv.DictReader(source, delimiter='\t', quoting=csv.QUOTE_NONE)

def number(value, converter=int):
    return None if value == '\\N' else converter(value)

def import_data(credits=True):
    DATA.mkdir(exist_ok=True)
    target = DATA / 'movies.new.db'
    target.unlink(missing_ok=True)
    db = sqlite3.connect(target)
    db.executescript('''
        PRAGMA journal_mode=OFF;
        CREATE TABLE movies(id TEXT PRIMARY KEY, title TEXT, original TEXT, search_title TEXT, search_original TEXT, year INTEGER, runtime INTEGER, genres TEXT, rating REAL, votes INTEGER);
        CREATE TABLE credits(id TEXT, person TEXT, category TEXT, position INTEGER);
        CREATE TABLE names(id TEXT PRIMARY KEY, name TEXT);
    ''')
    batch = []
    for row in rows('title.basics'):
        if row['titleType'] != 'movie' or row['isAdult'] != '0':
            continue
        batch.append((row['tconst'], row['primaryTitle'], row['originalTitle'], row['primaryTitle'].casefold(), row['originalTitle'].casefold(), number(row['startYear']), number(row['runtimeMinutes']), '' if row['genres'] == '\\N' else row['genres'], None, None))
        if len(batch) == 10000:
            db.executemany('INSERT INTO movies VALUES(?,?,?,?,?,?,?,?,?,?)', batch)
            batch.clear()
    db.executemany('INSERT INTO movies VALUES(?,?,?,?,?,?,?,?,?,?)', batch)
    ids = {row[0] for row in db.execute('SELECT id FROM movies')}
    logging.info('Imported %s movies', len(ids))
    db.executemany('UPDATE movies SET rating=?, votes=? WHERE id=?', ((float(r['averageRating']), int(r['numVotes']), r['tconst']) for r in rows('title.ratings') if r['tconst'] in ids))
    if credits:
        people = set()
        for row in rows('title.crew'):
            if row['tconst'] in ids and row['directors'] != '\\N':
                directors = row['directors'].split(',')
                people.update(directors)
                db.executemany('INSERT INTO credits VALUES(?,?,?,?)', ((row['tconst'], p, 'director', i) for i, p in enumerate(directors)))
        logging.info('Imported directors')
        for row in rows('title.principals'):
            if row['tconst'] in ids and row['category'] in ('actor', 'actress', 'self'):
                people.add(row['nconst'])
                db.execute('INSERT INTO credits VALUES(?,?,?,?)', (row['tconst'], row['nconst'], 'cast', int(row['ordering'])))
        logging.info('Imported cast')
        db.executemany('INSERT INTO names VALUES(?,?)', ((r['nconst'], r['primaryName']) for r in rows('name.basics') if r['nconst'] in people))
    db.execute('CREATE INDEX credit_title ON credits(id)')
    db.commit()
    db.close()
    target.replace(DATA / 'movies.db')
    logging.info('Database ready')

if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(message)s')
    parser = argparse.ArgumentParser()
    parser.add_argument('--skip-credits', action='store_true')
    args = parser.parse_args()
    import_data(not args.skip_credits)
