package lux;

import lux.junit.SearchTestRunner;

import org.junit.runner.RunWith;

/**
 * executes all the PathQueryTest test cases using path indexes and compares results against
 * an unindexed/unoptimized baseline.
 *
 */

@RunWith(SearchTestRunner.class)
public class SearchPathQueryTest extends QNameQueryTest {
	// tests are run defined in SearchPathQueryTest.xml and loaded by the test runner
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
