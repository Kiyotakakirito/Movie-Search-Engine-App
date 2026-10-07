from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs, urlencode
import json
import logging
import os
import sqlite3
import urllib.request
import argparse
from contextlib import closing

DATABASE = Path(__file__).parent / 'data' / 'movies.db'
SOURCE = 'IMDb dataset'

def enrich(movie):
    key = os.environ.get('OMDB_API_KEY')
    if not key:
        return movie
    try:
        url = 'https://www.omdbapi.com/?' + urlencode({'apikey': key, 'i': movie['id'], 'plot': 'full'})
        with urllib.request.urlopen(url, timeout=8) as response:
            extra = json.load(response)
        if extra.get('Response') == 'True':
            movie['poster'] = extra.get('Poster') if extra.get('Poster') not in (None, 'N/A') else None
            movie['description'] = extra.get('Plot') if extra.get('Plot') not in (None, 'N/A') else None
    except Exception:
        logging.warning('Optional enrichment failed for %s', movie['id'])
    return movie

def search(query):
    escaped = query.casefold().replace('\\', '\\\\').replace('%', '\\%').replace('_', '\\_')
    pattern = '%' + escaped + '%'
    with closing(sqlite3.connect(f'{DATABASE.as_uri()}?mode=ro', uri=True)) as db:
        db.row_factory = sqlite3.Row
        condition = "search_title LIKE ? ESCAPE '\\' OR search_original LIKE ? ESCAPE '\\'"
        total = db.execute(f'SELECT COUNT(*) FROM movies WHERE {condition}', (pattern, pattern)).fetchone()[0]
        result = db.execute(f'SELECT * FROM movies WHERE {condition} ORDER BY CASE WHEN search_title=? THEN 0 ELSE 1 END, COALESCE(votes,0) DESC, title, id LIMIT 40', (pattern, pattern, query.casefold()))
        movies = []
        for row in result:
            credits = db.execute('SELECT names.name, credits.category FROM credits JOIN names ON names.id=credits.person WHERE credits.id=? ORDER BY credits.position', (row['id'],)).fetchall()
            movie = {key: row[key] for key in ('id', 'title', 'year', 'runtime', 'rating', 'votes')}
            movie.update(genres=row['genres'].split(',') if row['genres'] else [], directors=list(dict.fromkeys(r['name'] for r in credits if r['category'] == 'director')), cast=list(dict.fromkeys(r['name'] for r in credits if r['category'] == 'cast')), poster=None, description=None)
            movies.append(movie)
    if os.environ.get('OMDB_API_KEY'):
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(max_workers=10) as pool:
            movies = list(pool.map(enrich, movies))
    return {'movies': movies, 'total': total, 'limit': 40, 'source': SOURCE}

class Handler(BaseHTTPRequestHandler):
    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        if SOURCE == 'Sample data' and parsed.path.startswith('/posters/'):
            name = Path(parsed.path).name
            path = Path(__file__).parent / 'sample_posters' / name
            if path.is_file() and path.suffix == '.png':
                body = path.read_bytes()
                self.send_response(200)
                self.send_header('Content-Type', 'image/png')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            self.send_json(404, {'error': 'Poster not found'})
            return
        if parsed.path == '/health':
            self.send_json(200 if DATABASE.exists() else 503, {'ready': DATABASE.exists()})
            return
        if parsed.path != '/search':
            self.send_json(404, {'error': 'Endpoint not found'})
            return
        query = parse_qs(parsed.query).get('q', [''])[0].strip()
        if not query or len(query) > 200:
            self.send_json(400, {'error': 'Enter a title between 1 and 200 characters'})
            return
        if not DATABASE.exists():
            self.send_json(503, {'error': 'Run import_data.py first'})
            return
        try:
            result = search(query)
            if SOURCE == 'Sample data':
                for movie in result['movies']:
                    movie['poster'] = '/posters/' + movie['id'] + '.png'
                    movie['description'] = 'A fictional space adventure created for the sample-data demonstration.'
            logging.info('query=%s response/result count=%s total=%s', query, len(result['movies']), result['total'])
            self.send_json(200, result)
        except Exception:
            logging.exception('Search failed')
            self.send_json(500, {'error': 'Search failed'})

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--demo', action='store_true')
    args = parser.parse_args()
    if args.demo:
        DATABASE = Path(__file__).parent / 'data' / 'sample.db'
        SOURCE = 'Sample data'
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(message)s')
    ThreadingHTTPServer(('0.0.0.0', 8000), Handler).serve_forever()
