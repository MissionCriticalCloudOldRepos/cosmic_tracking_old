import unittest

from marvin.marvinLog import MarvinLog

class TestMarvinLog(unittest.TestCase):

    def test_create_marvin_log(self):
        marvin_log = MarvinLog('test-log')

        self.assertIsNotNone(marvin_log)
