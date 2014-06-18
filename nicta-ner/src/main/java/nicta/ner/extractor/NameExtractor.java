/*
 * #%L
 * NICTA Named Entity Recogniser library
 * %%
 * Copyright (C) 2010 - 2014 NICTA
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
package nicta.ner.extractor;

import nicta.ner.NERResultSet;
import nicta.ner.data.DatePhraseModel;
import nicta.ner.data.Phrase;
import nicta.ner.data.Phrase_Date;
import nicta.ner.data.Phrase_Name;
import nicta.ner.resource.Configuration;
import nicta.ner.util.Dictionary;
import nicta.ner.util.IO;
import nicta.ner.util.JTokenizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rule-based expert system. */
public class NameExtractor {

    private static final JTokenizer TOKENIZER = new JTokenizer(JTokenizer.TOKENIZER_MODE.WITH_PUNCTUATE);
    private static final Set<String> NON_NAME_WORDS;
    private final Dictionary dict = Dictionary.getSharedDictionary();

    static {
        try { NON_NAME_WORDS = IO.lowerCasedWordSet(NameExtractor.class, "NON_NAME_WORDS"); }
        catch (final IOException ioe) { throw new RuntimeException(ioe); }
    }

    private List<List<Phrase>> phrases;
    private List<List<String>> tokens;

    private final int nameTypeScoreDimension;

    /** Constructor to build the Named Entity Extractor instance. */
    public NameExtractor(final Configuration conf) {
        nameTypeScoreDimension = conf.getNameTypes().size();
    }

    /** Call this method to return the result <b>after</b> the process(String _text) method had been called. */
    public NERResultSet getResult() {
        final NERResultSet ner = new NERResultSet();
        ner.phrases = phrases;
        ner.tokens = tokens;
        return ner;
    }

    /**
     * This method will parse the text input into tokens and name phrases.
     * Call getResult() method to get the processed result.
     */
    public void process(final String _text) {
        // tokenization
        tokens = TOKENIZER.process(_text);

        // extract name phrase:
        phrases = new ArrayList<>();

        for (final List<String> token : tokens) {
            // for each sentence
            final List<Phrase> sentencePhrase = new ArrayList<>();
            int wordPtr = 0;
            int phraseStubStartPtr = 0;
            int phraseStubLength;
            while (wordPtr < token.size()) {

                // iterate through every words in the sentence
                final List<String> currentPhrase = new ArrayList<>();

                // get the phrase stub
                while (true) {
                    // we merge the names combined with the word 'and'
                    // if the second phrase is much shorter than the first phrase
                    // example: Canberra Institution of Science and Technology
                    boolean mergeAndFlag = false;
                    if (!"and".equalsIgnoreCase(token.get(wordPtr))) {
                        //mergeAndFlag = false;
                    }
                    else if (currentPhrase.isEmpty()) {
                        //mergeAndFlag = false;
                    }
                    else if (!(wordPtr + 1 < token.size()
                               && detectNameWordInSentenceByPosition(token, wordPtr + 1))) {
                        //mergeAndFlag = false;
                    }
                    else {
                        if (wordPtr + currentPhrase.size() - 1 >= token.size()) {
                            mergeAndFlag = true;
                        }
                        else {
                            for (int i = wordPtr + 1; i <= wordPtr + currentPhrase.size() - 1; i++) {
                                if (!detectNameWordInSentenceByPosition(token, i)) {
                                    mergeAndFlag = true;
                                    break;
                                }
                            }
                        }
                    }

                    if
                            (detectNameWordInSentenceByPosition(token, wordPtr) ||
                             (
                                     (token.get(wordPtr).equalsIgnoreCase("of") || token.get(wordPtr)
                                                                                        .equalsIgnoreCase(
                                                                                                "the")
                                      ||
                                      token.get(wordPtr).equalsIgnoreCase("'s"))
                                     &&
                                     !currentPhrase.isEmpty() &&
                                     wordPtr + 1 < token.size() && detectNameWordInSentenceByPosition(
                                             token, wordPtr + 1)
                             ) ||
                             (
                                     mergeAndFlag
                             )
                            ) {
                        if (currentPhrase.isEmpty()) phraseStubStartPtr = wordPtr; // record the index of the first word
                        currentPhrase.add(token.get(wordPtr));
                        wordPtr++;
                        if (wordPtr == token.size()) break;
                    }
                    else {
                        break;
                    }
                }
                phraseStubLength = currentPhrase.size();

                //get the attached 'the' or 'a'
                int phrasePos = phraseStubStartPtr;
                int phraseLen = currentPhrase.size();

                if (phrasePos
                    > 0) {    // if phrasePos is zero, then it is the first word, there won't be any 'the' or 'a' attached to this word.
                    final String attachedArticle = token.get(phrasePos - 1);
                    if ("the".equalsIgnoreCase(attachedArticle) || "a".equalsIgnoreCase(attachedArticle)) {
                        phrasePos--;
                        phraseLen++;
                    }
                }

                //handling non-name single words such as: Monday, January, etc.
                if (phraseStubLength == 1 && phraseStubStartPtr == phrasePos) {
                    if (NON_NAME_WORDS.contains(currentPhrase.get(0).toLowerCase())) {
                        currentPhrase.clear();
                        wordPtr--;
                    }
                }

                if (!currentPhrase.isEmpty()) {
                    if (!(currentPhrase.size() == 1 &&
                          (Dictionary.isPastTense(currentPhrase.get(0)) || Dictionary.isPlural(currentPhrase.get(0)))
                    )) {
                        final String[] currentPhraseArray = new String[currentPhrase.size()];
                        for (int i = 0; i < currentPhrase.size(); i++)
                            currentPhraseArray[i] = currentPhrase.get(i);
                        sentencePhrase.add(new Phrase_Name(currentPhraseArray, phrasePos, phraseLen, phraseStubStartPtr,
                                                           nameTypeScoreDimension));
                    }
                }
                else {
                    // if the current pointed word is not a name phrase
                    // ESTIMATE IF IT IS A TIME/DATE
                    final List<String> currentDatePhrase = new ArrayList<>();
                    final DatePhraseModel dpm = new DatePhraseModel();
                    int tempPtr = wordPtr;
                    while (true) {
                        final String word = token.get(tempPtr);
                        final int type = Phrase_Date.getDateType(word);
                        if (
                                (
                                        type >= 0
                                ) ||
                                (
                                        (word.equalsIgnoreCase("of") || word.equalsIgnoreCase(",") || word
                                                .equalsIgnoreCase("the")) &&
                                        !currentDatePhrase.isEmpty() && tempPtr + 1 != token.size() &&
                                        Phrase_Date.getDateType(token.get(tempPtr + 1)) >= 0 &&
                                        Phrase_Date.getDateType(token.get(tempPtr - 1)) >= 0
                                )
                                ) {
                            // is date word:
                            currentDatePhrase.add(word);
                            dpm.addType(type);
                            tempPtr++;
                            if (tempPtr == token.size()) break;
                        }
                        else {
                            break;
                        }
                    }
                    if (dpm.isDate()) {
                        // add the phrase to phrase array
                        final String[] currentPhraseArray = new String[currentDatePhrase.size()];
                        for (int i = 0; i < currentDatePhrase.size(); i++)
                            currentPhraseArray[i] = currentDatePhrase.get(i);
                        final Phrase newPhrase =
                                new Phrase_Date(currentPhraseArray, wordPtr, currentPhraseArray.length, wordPtr,
                                                nameTypeScoreDimension);
                        sentencePhrase.add(newPhrase);
                        wordPtr = tempPtr;
                    }
                }

                wordPtr++;
            }

            phrases.add(sentencePhrase);
        }

        // find attached words(preps):
        for (int si = 0; si < tokens.size(); si++) {
            final List<String> sentenceToken = tokens.get(si);
            final List<Phrase> sentencePhrase = phrases.get(si);
            for (int pi = 0; pi < sentencePhrase.size(); pi++) {
                getAttachedPrep(sentenceToken, sentencePhrase, pi);
            }
        }
    }

    /**
     * Detects if a particular word in a sentence is a name.
     */
    private boolean detectNameWordInSentenceByPosition(final List<String> _text, final int _pos) {
        boolean isFirstWord = false;
        boolean nextWordIsName = false;
        if (_pos == 0
            || !Character.isLetterOrDigit((_text.get(_pos - 1).charAt(0)))
            //|| (_text.get(_pos - 1).equals("\""))
            //|| (_text.get(_pos - 1).equals(":"))
                ) {
            isFirstWord = true;
            nextWordIsName = _text.size() > _pos + 1 ?
                             (
                                     _text.get(_pos + 1).equalsIgnoreCase("of") ||
                                     _text.get(_pos + 1).equalsIgnoreCase("'s") ? (_text.size() > _pos + 2
                                                                                   ? detectNameWord(_text.get(_pos + 2),
                                                                                                    false, false)
                                                                                   : false) :
                                     detectNameWord(_text.get(_pos + 1), false, false)
                             ) : false;
        }
        //noinspection UnnecessaryLocalVariable
        final boolean isName = detectNameWord(_text.get(_pos), isFirstWord, nextWordIsName);

        /*
        String wordType = dict.checkup(_text.get(_pos).toLowerCase());
        if (isFirstWord && !isName && wordType != null && wordType.startsWith("JJ")) {
            // if the first word is determined not to be a name but it is an adj.,
            // and if the second word is a name, we consider the first word to be a name as well.
            if (detectNameWord(_text.get(_pos + 1), false))
                return true;
        }
        */

        return isName;
    }

    /**
     * This method detects a word if it is a potential name word.
     */
    private boolean detectNameWord(final String _text, final boolean isFirstWord, final boolean nextWordIsName) {
        if (detectMultiCharCapital(_text)) {
            return true;
        }
        else if (detectFirstCharCapital(_text)) {
            if (!isFirstWord)
                return true;
            else {
                // if the word is the first word in a sentence and
                // ends with -ly (adv) -> consider it not a name
                if (_text.endsWith("ly"))
                    return false;
                // we need to deal with the first word in the sentence very carefully.
                // as we can not tell if the first word is a name by detecting upper case characters.
                final String type_original = dict.checkup(_text);
                final String type_lowercase = dict.checkup(_text.toLowerCase());
                if (type_original == null) {
                    if (type_lowercase == null) {
                        return true;    // if the word cannot be found in the dictionary, we consider it as a name entity.
                    }
                    /*
                    else {
                        return false;
                    }
                    */
                }
                else if (type_original.equals("NNP"))
                    return true;
                if (type_lowercase != null && (
                        type_lowercase.startsWith("IN")
                        || type_lowercase.startsWith("CC")
                        || type_lowercase.startsWith("RB")
                        || type_lowercase.startsWith("WRB")
                        // || type_lowercase.startsWith("JJ")
                        || type_lowercase.startsWith("."))) return false;
                else {
                    return nextWordIsName;
                }
            }
        }
        else {
            return false;
        }
    }

    /**
     * This method will find all the attached preps of a phrase.
     */
    protected void getAttachedPrep(final List<String> sentenceToken, final List<Phrase> sentencePhrase,
                                   final int index) {
        String prep = null;
        boolean nameSequenceMeetEnd = true;
        final Set<Phrase> phraseSequence = new HashSet<>();

        int phrasePtr = index;
        Phrase currentNamePhrase = sentencePhrase.get(phrasePtr);
        int phrasePtrInSentence;

        if (currentNamePhrase.attachedWordMap.get("prep") != null) return;

        // we need to find out all the name phrases in a sequence:
        // Example: Students who came from China, America and Australia are here.
        // in the example above, China, America and Australia are three name phrases all attached to the prep: from.
        // first loop, search forward to find all the names before the pointer and the attached prep
        while (prep == null) {
            currentNamePhrase = sentencePhrase.get(phrasePtr);
            phrasePtrInSentence = currentNamePhrase.phrasePosition;

            if (phrasePtrInSentence == 0) return;

            final String attachedWord = sentenceToken.get(phrasePtrInSentence - 1);

            // if the attached word is a comma or 'and'/'or', we consider it as a conj.
            if (",".equalsIgnoreCase(attachedWord)) {
                nameSequenceMeetEnd = false;
                phraseSequence.add(currentNamePhrase);
            }
            else if ("and".equalsIgnoreCase(attachedWord) || "or".equalsIgnoreCase(attachedWord)) {
                // meet end
                phraseSequence.add(currentNamePhrase);
                nameSequenceMeetEnd = true;
            }
            else if (dict.checkup(attachedWord.toLowerCase()) != null && dict.checkup(attachedWord.toLowerCase())
                                                                             .startsWith("IN")) {
                prep = attachedWord;
                phraseSequence.add(currentNamePhrase);
                break;
            }
            else {
                return;
            }
            phrasePtr--;
            if (phrasePtr < 0) return;
            if (sentencePhrase.get(phrasePtr).isDate) return;
            if (sentencePhrase.get(phrasePtr).phrasePosition + sentencePhrase.get(phrasePtr).phraseLength + 1
                != phrasePtrInSentence) return;
            // method terminates if the phrase before is not next to this phrase. This means the name sequence is broken.
        }

        //if(sentence.phrases.get(index).phrase[0].equalsIgnoreCase("Florida"))
        //	System.out.println("First time Florida: " + prep);

        phrasePtr = index + 1;

        // second loop, search backward to find the names behind the pointer
        while (!nameSequenceMeetEnd) {
            if (phrasePtr == sentencePhrase.size()) return;

            currentNamePhrase = sentencePhrase.get(phrasePtr);
            if (currentNamePhrase.isDate) return;
            phrasePtrInSentence = currentNamePhrase.phrasePosition;

            if (sentencePhrase.get(phrasePtr - 1).phrasePosition + sentencePhrase.get(phrasePtr - 1).phraseLength + 1
                != currentNamePhrase.phrasePosition) return;
            // method terminates if the phrase after is not next to this phrase.

            final String attachedWord = sentenceToken.get(phrasePtrInSentence - 1);
            // if the attached word is a comma or 'and'/'or', we consider it as a conj.
            if (",".equalsIgnoreCase(attachedWord)) {
                phraseSequence.add(currentNamePhrase);
            }
            else if ("and".equalsIgnoreCase(attachedWord) || "or".equalsIgnoreCase(attachedWord)) {
                // meet end
                phraseSequence.add(currentNamePhrase);
                nameSequenceMeetEnd = true;
                break;
            }
            else {
                return;
            }

            phrasePtr++;
        }

        // finally, attach the prep with the words in the phraseSequence
        for (final Phrase name : phraseSequence) {
            name.attachedWordMap.put("prep", prep);
        }
    }


    /**
     * This method detects if the word's first character is in capital size.
     * Returns true if it is; false if it is not.
     */
    private static boolean detectFirstCharCapital(final String word) {
        return !"i".equalsIgnoreCase(word)
               && detectLegalCharacter(word.charAt(0))
               && Character.isUpperCase(word.charAt(0));
    }

    /**
     * This method detects if the word has more than one character which is capital sized.
     * Returns true if there are more than one character upper cased; return false if less than one character is upper cased.
     */
    private static boolean detectMultiCharCapital(final String word) {
        if ("i".equalsIgnoreCase(word)) return false;
        int capCharCount = 0;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isUpperCase(word.charAt(i))) capCharCount++;
            if (capCharCount == 2) return true;
        }
        return false;
    }

    /**
     * This method tests if the input character is a legal character (a-z || A-Z).
     */
    private static boolean detectLegalCharacter(final char _c) {
        final char c = Character.toLowerCase(_c);
        return "abcdefghijklmnopqrstuvwxyz".indexOf(c) != -1;
    }
}
