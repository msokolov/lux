package lux.index.analysis;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.Reader;

import org.apache.lucene.analysis.ReusableAnalyzerBase;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.util.Version;

/**
 * An Analyzer that uses {@link WhitespaceTokenizer}, version 3.4, and inserts
 * position gaps of 100 between multiple values to inhibit phrase and span matches
 * across values.
 **/
public final class WhitespaceGapAnalyzer extends ReusableAnalyzerBase {
  
  @Override
  protected TokenStreamComponents createComponents(final String fieldName,
      final Reader reader) {
    return new TokenStreamComponents(new WhitespaceTokenizer(Version.LUCENE_34, reader));
  }
  
  /**
   * @return 100 - note this is tied to Surround QueryParser's maximum distance of 99.
   */
  @Override
  public int getPositionIncrementGap (String field) {
      return 100;
  }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
