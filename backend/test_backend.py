import gzip
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import import_data
import server

class DatasetTest(unittest.TestCase):
    def test_import_search_and_missing_fields(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            datasets = {
                'title.basics': 'tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres\ntt01\tmovie\tCafé 100%\tCafé 100%\t0\t2020\t\\N\t95\tDrama\ntt02\tmovie\tAnother Film\tOther Original\t0\t\\N\t\\N\t\\N\t\\N\ntt03\ttvSeries\tCafé Show\tCafé Show\t0\t2020\t\\N\t20\tComedy\ntt04\tmovie\tAdult Film\tAdult Film\t1\t2020\t\\N\t80\tDrama\n',
                'title.ratings': 'tconst\taverageRating\tnumVotes\ntt01\t7.8\t1500\n',
                'title.crew': 'tconst\tdirectors\twriters\ntt01\tnm01\t\\N\n',
                'title.principals': 'tconst\tordering\tnconst\tcategory\tjob\tcharacters\ntt01\t1\tnm02\tactor\t\\N\t[]\n',
                'name.basics': 'nconst\tprimaryName\tbirthYear\tdeathYear\tprimaryProfession\tknownForTitles\nnm01\tA Director\t\\N\t\\N\tdirector\ttt01\nnm02\tAn Actor\t\\N\t\\N\tactor\ttt01\n'
            }
            for name, content in datasets.items():
                with gzip.open(root / f'{name}.tsv.gz', 'wt', encoding='utf-8') as output:
                    output.write(content)
            with patch.object(import_data, 'DATA', root):
                import_data.import_data()
            with patch.object(server, 'DATABASE', root / 'movies.db'), patch.dict('os.environ', {}, clear=True):
                movie = server.search('CAFÉ')['movies'][0]
                self.assertEqual('Café 100%', movie['title'])
                self.assertEqual(7.8, movie['rating'])
                self.assertEqual(['A Director'], movie['directors'])
                self.assertEqual(['An Actor'], movie['cast'])
                self.assertEqual(1, server.search('%')['total'])
                self.assertEqual(0, server.search('_')['total'])
                missing = server.search('other original')['movies'][0]
                self.assertIsNone(missing['year'])
                self.assertIsNone(missing['runtime'])
                self.assertEqual([], missing['genres'])
                self.assertEqual(0, server.search('Adult Film')['total'])
                self.assertEqual(0, server.search('Café Show')['total'])
                self.assertEqual(0, server.search("' OR 1=1 --")['total'])

if __name__ == '__main__':
    unittest.main()
