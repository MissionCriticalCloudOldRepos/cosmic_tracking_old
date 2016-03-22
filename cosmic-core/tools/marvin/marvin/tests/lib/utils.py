import unittest

from test_utils import (
    MockApiClient,
    MockObject
)

from lib.utils import validateState

from codes import (
    FAIL,
    PASS
)

class TestUtils(unittest.TestCase):

    @classmethod
    def list(cls, apiclient, **kwargs):
        return(apiclient.listMockObjects(None))

    def test_validateState_succeeds_before_retry_limit(self):
        retries = 2
        timeout = 3
        api_client = MockApiClient(retries, 'initial state', 'final state')
        state = validateState(api_client, self, 'final state', timeout=timeout, interval=1)

        self.assertEqual(state, [PASS, None])
        self.assertEqual(retries, api_client.retry_counter)


    def test_validateState_succeeds_at_retry_limit(self):
        retries = 3
        timeout = 3
        api_client = MockApiClient(retries, 'initial state', 'final state')
        state = validateState(api_client, self, 'final state', timeout=timeout, interval=1)

        self.assertEqual(state, [PASS, None])
        self.assertEqual(retries, api_client.retry_counter)


    def test_validateState_fails_after_retry_limit(self):
        retries = 3
        timeout = 2
        api_client = MockApiClient(retries, 'initial state', 'final state')
        state = validateState(api_client, self, 'final state', timeout=timeout, interval=1)

        self.assertEqual(state, [FAIL, 'TestUtils state not trasited to final state, operation timed out'])
        self.assertEqual(retries, api_client.retry_counter)

if __name__ == '__main__':
    unittest.main()
