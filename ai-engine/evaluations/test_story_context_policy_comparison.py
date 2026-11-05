import unittest

from story_context_policy_comparison import compare


class PolicyComparisonTest(unittest.TestCase):
    def test_compares_bounded_alternatives_and_requested_survival(self):
        candidates = [
            {"reference": "a", "kind": "SOURCE_FILE", "path": "README.md", "score": 100,
             "trust": "TECHNICAL_EVIDENCE", "grounded": True, "selected": True,
             "projected": True, "reason": "SELECTED_BY_RANK"},
            {"reference": "b", "kind": "SOURCE_FILE", "path": "src/App.java", "score": 99,
             "trust": "TECHNICAL_EVIDENCE", "grounded": False, "selected": False,
             "projected": False, "reason": "INSUFFICIENT_RELEVANCE"},
            {"reference": "c", "kind": "SOURCE_FILE", "path": "docs/decision.md", "score": 98,
             "trust": "TRUSTED", "grounded": False, "selected": False,
             "projected": False, "reason": "INSUFFICIENT_RELEVANCE"},
        ]

        result = compare(candidates, ["README.md"], maximum_items=2)

        self.assertEqual(set(result["policies"]), {"current", "balanced", "unrestricted"})
        self.assertEqual(result["policies"]["current"]["candidateByKind"]["SOURCE_FILE"], 3)
        self.assertEqual(result["policies"]["current"]["rejectedByKind"]["SOURCE_FILE"], 2)
        self.assertTrue(result["policies"]["unrestricted"]["requestedFileOutcomes"]["README.md"]["selected"])
        self.assertEqual(result["policies"]["current"]["selectedCount"], 1)

    def test_rejects_oversized_fixture(self):
        with self.assertRaises(ValueError):
            compare([{"reference": "a", "kind": "SOURCE_FILE"}] * 4097, [])


if __name__ == "__main__":
    unittest.main()
