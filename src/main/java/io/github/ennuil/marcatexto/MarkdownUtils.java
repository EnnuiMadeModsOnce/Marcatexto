package io.github.ennuil.marcatexto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

// TODO - Split this mod between the Text-based Markdown API and the chat mod
public class MarkdownUtils {
    // These regex patterns were adapted from the simple-markdown library, which is used by Discord
    // This guarantees that the markdown will be 100% Discord-compatible
    // Source: https://github.com/Khan/simple-markdown/blob/master/src/index.js
    private static final Pattern ESCAPE_PATTERN = Pattern.compile("\\\\([^0-9A-Za-z\\s])");
    private static final Pattern UNDERLINE_ITALIC_PATTERN = Pattern.compile("\\b_((?:__|\\\\.|[^\\\\_])+?)_\\b", Pattern.DOTALL);
    private static final Pattern ASTERISK_ITALIC_PATTERN = Pattern.compile("\\*(?=\\S)((?:\\*\\*|\\\\.|\\s+(?:\\\\.|[^\\s\\*\\\\]|\\*\\*)|[^\\s\\*\\\\])+?)\\*(?!\\*)", Pattern.DOTALL);
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*((?:\\\\.|[^\\\\])+?)\\*\\*(?!_)", Pattern.DOTALL);
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__((?:\\\\.|[^\\\\])+?)__(?!_)", Pattern.DOTALL);
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(?=\\S)((?:\\\\.|~(?!~)|[^\\s~\\\\]|\\s(?!~~))+?)~~", Pattern.DOTALL);
    private static final Pattern SPOILER_PATTERN = Pattern.compile("\\|\\|(?=\\S)((?:\\\\.|~(?!\\|)|[^\\s\\|\\\\]|\\s(?!\\|\\|))+?)\\|\\|", Pattern.DOTALL);

    private static final String PLAIN_TOKEN = "T";
    private static final String ITALIC_TOKEN = "/";
    private static final String BOLD_TOKEN = "*";
    private static final String UNDERLINE_TOKEN = "_";
    private static final String STRIKETHROUGH_TOKEN = "~";
    private static final String SPOILER_TOKEN = "|";

    public static Text parseMarkdownMessage(String message) {
        System.out.println(message);
        //String sanitizedMessage = message.replaceAll("([()])", "\\\\$1");
        String sanitizedMessage = message.replaceAll("(?<!\\\\)([{}])", "\\\\$1");;

        System.out.println(sanitizedMessage);
        List<String> escapedChars = new ArrayList<>();
        int escapedCharCount = 0;
        while (ESCAPE_PATTERN.matcher(sanitizedMessage).find()) {
            Matcher escapeMatcher = ESCAPE_PATTERN.matcher(sanitizedMessage);
            if (escapeMatcher.find()) {
                escapedChars.add(escapeMatcher.group(1));
                escapedCharCount++;
                sanitizedMessage = escapeMatcher.replaceFirst("{" + escapedCharCount + "}");
                System.out.println(sanitizedMessage);
            }
        }
        sanitizedMessage = sanitizedMessage.replaceAll("([()])", "\\\\$1");
        sanitizedMessage = sanitizedMessage.replaceAll("\\\\([{}])", "$1");
        sanitizedMessage = sanitizedMessage.replaceAll("\\\\([0-9A-Za-z\\s])", "\\\\$1");
        String tokenizedMessage = String.format("(%s:%s)", PLAIN_TOKEN, sanitizedMessage);

        tokenizedMessage = processPattern(tokenizedMessage, UNDERLINE_ITALIC_PATTERN, String.format("(%s:$1)", ITALIC_TOKEN));
        tokenizedMessage = processPattern(tokenizedMessage, ASTERISK_ITALIC_PATTERN, String.format("(%s:$1)", ITALIC_TOKEN));
        tokenizedMessage = processPattern(tokenizedMessage, BOLD_PATTERN, String.format("(%s:$1)", BOLD_TOKEN));
        tokenizedMessage = processPattern(tokenizedMessage, UNDERLINE_PATTERN, String.format("(%s:$1)", UNDERLINE_TOKEN));
        tokenizedMessage = processPattern(tokenizedMessage, STRIKETHROUGH_PATTERN, String.format("(%s:$1)", STRIKETHROUGH_TOKEN));
        tokenizedMessage = processPattern(tokenizedMessage, SPOILER_PATTERN, String.format("(%s:$1)", SPOILER_TOKEN));
        System.out.println(tokenizedMessage);
        Text text = parseTokenizedMarkdownText(tokenizedMessage, LiteralText.EMPTY.shallowCopy(), escapedChars).text();
        if (text.getSiblings().size() == 1) {
            text = text.getSiblings().get(0);
        }
        System.out.println(text.toString());
        return text;
    }

    public static String processPattern(String tokenizedMessage, Pattern pattern, String token) {
        // This is what past me has done. I don't remember why, but that broke Discord parity
        /*
        while (pattern.matcher(tokenizedMessage).find()) {
            tokenizedMessage = pattern.matcher(tokenizedMessage).replaceAll(token);
        }
        */
        tokenizedMessage = pattern.matcher(tokenizedMessage).replaceAll(token);

        return tokenizedMessage;
    }

    private enum ParseStage {
        INIT,
        PARSE_STATEMENT,
        PARSE_TEXT,
        PARSE_ESCAPED_CHARACTER
    }

    private static ParseResult parseTokenizedMarkdownText(String tokenizedMessage, MutableText text, List<String> escapedChars) {
        ParseStage stage = ParseStage.INIT;
        int readCharacters = -1;
        String statementText = "";
        String message = "";
        boolean escapeNextChar = false;
        List<Text> texts = new ArrayList<>();
        String escapedCharacter = "";

        char[] tokenizedCharacters = tokenizedMessage.toCharArray();
        // This screams "ancient syntax", but well, it seems to be a little less uglier than "i = tokenizedCharacters.length"
        charReader:
        for (int i = 0; i < tokenizedCharacters.length; i++) {
            char character = tokenizedCharacters[i];
            readCharacters++;
            System.out.println(String.format("%s | %s | %s | %s | %s", character, stage, tokenizedMessage, escapeNextChar, i));
            
            switch (stage) {
                case INIT -> {
                    if (character == '(') {
                        stage = ParseStage.PARSE_STATEMENT;
                    }
                }
                case PARSE_STATEMENT -> {
                    if (character == ':') {
                        stage = ParseStage.PARSE_TEXT;
                    } else {
                        statementText += character;
                    }
                }
                case PARSE_TEXT -> {
                    if (character != '('
                    && character != ')'
                    && character != '\\'
                    && character != '{'
                    && character != '}'
                    ) {
                        message += character;
                    } else {
                        if (escapeNextChar) {
                            message += character;
                            escapeNextChar = false;
                        } else if (character == '\\') {
                            escapeNextChar = true;
                        } else {
                            if (character == '(') {
                                if (!message.isEmpty()) {
                                    texts.add(new LiteralText(message));
                                }
                                message = "";
                                ParseResult result = parseTokenizedMarkdownText(tokenizedMessage.substring(readCharacters), LiteralText.EMPTY.shallowCopy(), escapedChars);
                                texts.add(result.text);
                                i += result.readChars;
                                readCharacters += result.readChars;
                                continue charReader;
                            }

                            if (character == '{') {
                                stage = ParseStage.PARSE_ESCAPED_CHARACTER;
                                continue charReader;
                            }

                            if (!message.isEmpty()) {
                                texts.add(new LiteralText(message));
                            }

                            MutableText textToModify;
                            if (texts.size() == 1) {
                                textToModify = texts.get(0).shallowCopy();
                            } else {
                                textToModify = new LiteralText("");
                                textToModify.getSiblings().addAll(texts);
                            }

                            switch (statementText) {
                                case ITALIC_TOKEN -> textToModify.setStyle(textToModify.getStyle().withItalic(true));
                                case BOLD_TOKEN -> textToModify.setStyle(textToModify.getStyle().withBold(true));
                                case UNDERLINE_TOKEN -> textToModify.setStyle(textToModify.getStyle().withUnderline(true));
                                case STRIKETHROUGH_TOKEN -> textToModify.setStyle(textToModify.getStyle().withStrikethrough(true));
                                case SPOILER_TOKEN -> {
                                    // TODO - █████ ████
                                    MutableText spoilerText = new LiteralText("[Spoiler]");
                                    spoilerText.setStyle(
                                        spoilerText.getStyle()
                                            .withColor(0xE5E5E8)
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, textToModify))
                                    );
                                    textToModify = spoilerText;
                                }
                                default -> {}
                            }

                            text = textToModify;
                            message = "";
                            if (character == ')') break charReader;
                        }
                    }
                }
                case PARSE_ESCAPED_CHARACTER -> {
                    if (character != '}') {
                        escapedCharacter += character;
                    } else {
                        int escapedCharIndex = Integer.parseInt(escapedCharacter) - 1;
                        message += escapedChars.get(escapedCharIndex);
                        escapedCharacter = "";
                        stage = ParseStage.PARSE_TEXT;
                    }
                }
            }
        }
        
        return new ParseResult(text, readCharacters);
    }

    // TODO - Use List<Text> instead, in order to support Text-to-Markdown conversion
    public record ParseResult(Text text, int readChars) {};
}
