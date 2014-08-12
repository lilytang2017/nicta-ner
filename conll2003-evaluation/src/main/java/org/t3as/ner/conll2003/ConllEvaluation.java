/*
 * #%L
 * NICTA t3as NER CoNLL 2003 evaluation
 * %%
 * Copyright (C) 2014 NICTA
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.t3as.ner.conll2003;

import org.t3as.ner.NamedEntityAnalyser;
import org.t3as.ner.NerResultSet;
import org.t3as.ner.resource.Configuration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Parse the CoNLL 2003 test data into sentences, use NICTA t3as NER to analyse the text, and print out the results
 * so that they can be checked by the 'conlleval' tool. See here for more info:
 * <p/>
 * http://www.cnts.ua.ac.be/conll2003/ner/
 */
public class ConllEvaluation {

    private final File testInput;

    public ConllEvaluation(final File testInput) { this.testInput = testInput; }

    public void evaluate() throws IOException {
        final NamedEntityAnalyser nea = new NamedEntityAnalyser(new Configuration());

        try (final ConllReader r = new ConllReader(testInput)) {
            while (r.hasNext()) {
                // each section in the test results data starts with a DOCSTART
                System.out.println("-DOCSTART- -X- O O O\n");
                final Collection<Sentence> sentences = r.next();

                for (final Sentence conllSentence : sentences) {
                    final NerResultSet nerResultSet = nea.process(conllSentence.sentence);
                    final Map<Integer, NerClassification> phraseMap = Util.positionClassificationMap(nerResultSet);

                    ConllToken previousToken = null;
                    for (final ConllToken conllToken : conllSentence.tokens) {
                        final NerClassification nerClas = phraseMap.get(conllToken.startIndex);
                        final NerClassification previousClas = previousToken == null
                                                               ? null : phraseMap.get(previousToken.startIndex);
                        final String clas = Util.translateClassification(nerClas, previousClas);

                        // print out the token, the test annotations, and our classification of the token
                        System.out.printf("%s %s %s %s\n",
                                          conllToken.token, conllToken.classifiers, conllToken.truth, clas);
                        previousToken = conllToken;
                    }
                    // finish each sentence with a newline
                    System.out.println();
                }
            }
        }
    }
}
