/* Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package org.apache.myfaces.tobago.util;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.apache.myfaces.tobago.internal.util.HtmlWriterHelper;
public class HtmlWriterHelperUnitTest {
    // some chars must escaped in attribute values other than in text
    // put them at beginning of raw texts and in both escaped texts
    // HTML 4.0, section B.7.1: ampersands followed by
    // an open brace don't get escaped
    private static final String[] RAW_TEXTS = new String[]{ "oeffnende spitze klammern werden in attributen doch escaped <tagname >", "& followed by an { -> &{ don't get escaped in attributes", "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ", " ¡¢£¤¥¦§¨©ª«¬­®¯", "°±²³´µ¶·¸¹º»¼½¾¿", "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏ", "ÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞß", "àáâãäåæçèéêëìíîï", "ðñòóôõö÷øùúûüýþÿ" };

    private static final String[] ESCAPED_TEXTS = new String[]{ "oeffnende spitze klammern werden in attributen doch escaped &lt;tagname &gt;", "&amp; followed by an { -&gt; &amp;{ don&#x27;t get escaped in attributes", RAW_TEXTS[2]// no escape needed
    , "&nbsp;&iexcl;&cent;&pound;&curren;&yen;&brvbar;&sect;&uml;&copy;&ordf;&laquo;&not;&shy;&reg;&macr;", "&deg;&plusmn;&sup2;&sup3;&acute;&micro;&para;&middot;&cedil;&sup1;&ordm;&raquo;&frac14;&frac12;" + "&frac34;&iquest;", "&Agrave;&Aacute;&Acirc;&Atilde;&Auml;&Aring;&AElig;&Ccedil;&Egrave;&Eacute;&Ecirc;&Euml;&Igrave;&Iacute;" + "&Icirc;&Iuml;", "&ETH;&Ntilde;&Ograve;&Oacute;&Ocirc;&Otilde;&Ouml;&times;&Oslash;&Ugrave;&Uacute;&Ucirc;&Uuml;&Yacute;" + "&THORN;&szlig;", "&agrave;&aacute;&acirc;&atilde;&auml;&aring;&aelig;&ccedil;&egrave;&eacute;&ecirc;&euml;&igrave;&iacute;" + "&icirc;&iuml;", "&eth;&ntilde;&ograve;&oacute;&ocirc;&otilde;&ouml;&divide;&oslash;&ugrave;&uacute;&ucirc;&uuml;&yacute;" + "&thorn;&yuml;" };

    private static final String[] ESCAPED_ATTRIBUTES = new String[]{ ESCAPED_TEXTS[0]// same as in texts
    , "&amp; followed by an { -&gt; &{ don&#x27;t get escaped in attributes", ESCAPED_TEXTS[2]// same as in texts
    , ESCAPED_TEXTS[3]// same as in texts
    , ESCAPED_TEXTS[4]// same as in texts
    , ESCAPED_TEXTS[5]// same as in texts
    , ESCAPED_TEXTS[6]// same as in texts
    , ESCAPED_TEXTS[7]// same as in texts
    , ESCAPED_TEXTS[8]// same as in texts
     };

    @Test
    public void testTexts() {
        final CharArrayWriter writer = new CharArrayWriter();
        final HtmlWriterHelper helper = new HtmlWriterHelper(writer, StandardCharsets.ISO_8859_1);
        for (int i = 0; i < ESCAPED_TEXTS.length; i++) {
            testText(helper, writer, RAW_TEXTS[i], ESCAPED_TEXTS[i]);
        }
    }

    @Test
    public void testAttributes() {
        final CharArrayWriter writer = new CharArrayWriter();
        final HtmlWriterHelper helper = new HtmlWriterHelper(writer, StandardCharsets.ISO_8859_1);
        for (int i = 0; i < ESCAPED_ATTRIBUTES.length; i++) {
            testAttributeValue(helper, writer, RAW_TEXTS[i], ESCAPED_ATTRIBUTES[i]);
        }
    }

    private void testText(final HtmlWriterHelper writerUtil, final CharArrayWriter writer, final String text, final String escaped) {
        try {
            writer.reset();
            writerUtil.writeText(text);
            final String result = String.valueOf(writer.toCharArray());
            Assertions.assertEquals(escaped, result);
        } catch (final IOException e) {
            // could not occur with CharArrayWriter
        }
    }

    private void testAttributeValue(final HtmlWriterHelper writerUtil, final CharArrayWriter writer, final String text, final String escaped) {
        try {
            writer.reset();
            writerUtil.writeAttributeValue(text);
            final String result = String.valueOf(writer.toCharArray());
            Assertions.assertEquals(escaped, result);
        } catch (final IOException e) {
            // could not occur with CharArrayWriter
        }
    }
}
