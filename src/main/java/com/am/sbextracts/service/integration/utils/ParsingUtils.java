package com.am.sbextracts.service.integration.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import com.am.sbextracts.client.BambooHrSignedFileClient;
import com.am.sbextracts.model.InternalSlackEventResponse;

import feign.Response;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public final class ParsingUtils {

    private static final String ONCLICK = "onclick";
    private static final String CLASS = "class";
    public static final String SVERKA = "sverka";
    public static final String AKT = "akt";
    public static final String TH_CONTRACT = "THcontract";

    public static String getInn(TagNode tag) {
        return StringUtils.strip(((ContentNode) tag.getAllChildren().get(0)).getContent()).split("\\.")[1];
    }

    public static String getInn(String fileName) {
        return StringUtils.strip(fileName).split("\\.")[1];
    }

    public static String getFileTitle(TagNode tag) {
        return StringUtils.strip(((ContentNode) tag.getAllChildren().get(0)).getContent());
    }

    // previewFile('24976', '24980');
    public static int getFileId(TagNode tag) {
        return getItem(tag, 1);
    }

    public static boolean isMarkedBySpecialSymbols(TagNode tag) {
        return StringUtils.containsAny(((ContentNode) tag.getAllChildren().get(0)).getContent(), SVERKA, TH_CONTRACT);
    }

    public static int getItem(TagNode tag, int index) {
        final Pattern pattern = Pattern.compile("'(.*?)'");
        final Matcher matcher = pattern.matcher(tag.getAttributes().get(ONCLICK));
        List<String> list = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                list.add(matcher.group(i));
            }
        }
        return Integer.parseInt(list.get(index));
    }

    public static int getTemplateFileId(TagNode tag) {
        return getItem(tag, 0);
    }


    public static TagNode getTagNode(Response.Body body) {
        try {
            return new HtmlCleaner().clean(body.asInputStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

//    public static boolean isSigned(TagNode tagNode) {
//        ContentNode node = (ContentNode) tagNode.getParent().getElementsByAttValue(CLASS, "ReportsTable__statusIcon",
//                true, false)[0].getParent().getAllChildren().get(2);
//        return Integer.parseInt(node.getContent()) > 0;
//    }

    public static boolean isSigned(BambooHrSignedFileClient.Document document) {
        return document.getCompleted() > 0;
    }

//    public static boolean isActorReconciliationAndDate(TagNode tagNode, String date) {
//        Optional<String> title = Arrays
//                .stream(tagNode.getElementsByAttValue(CLASS, "ReportsTable__reportNameText",
//                        true, false)).findFirst()
//                .map(at -> at.getAttributeByName("title"));
//        String[] splitResult = title.map(t -> t.split("\\."))
//                .orElseThrow(() -> new IllegalArgumentException("can not filter by akt and date"));
//        if (splitResult.length < 6) {
//            return false;
//        }
//        return StringUtils.equalsAny(splitResult[5], AKT, SVERKA)
//                && String.join(".", splitResult[2], splitResult[3], splitResult[4]).equals(date);
//    }

    public static boolean isSpecificDateAndDocumentType(BambooHrSignedFileClient.Document document, InternalSlackEventResponse slackEventResponse) {
        String[] splitResult = Optional.ofNullable(document.getName()).map(t -> t.split("\\."))
            .orElseThrow(() -> new IllegalArgumentException("can not filter by akt and date"));
        if (splitResult.length < 6) {
            return false;
        }
        return StringUtils.equalsAny(splitResult[5], slackEventResponse.getTypeOfDocuments())
            && String.join(".", splitResult[2], splitResult[3], splitResult[4]).equals(slackEventResponse.getDate());
    }

    public static final Predicate<TagNode> isRequiredTag = tagNode -> tagNode.getAttributes().containsKey(ONCLICK)
            && tagNode.getAttributeByName(ONCLICK).startsWith("previewFile");

    public static final BiPredicate<TagNode, InternalSlackEventResponse> FILTER_BY_DATE_AND_DOCUMENT_TYPE = (tagNode, slackEventResponse) -> {
        String contentNodeContent = StringUtils.strip(((ContentNode) tagNode.getAllChildren().get(0)).getContent());
        log.info("file name for parsing is: {}", contentNodeContent);
        String[] splitContent = contentNodeContent.split("\\.");
        if(splitContent.length < 6) {
            log.error("the filename does not match the filename format {}", contentNodeContent);
        }
        return StringUtils.equalsAny(splitContent[5], slackEventResponse.getTypeOfDocuments())
                && String.join(".", splitContent[2], splitContent[3], splitContent[4]).equals(slackEventResponse.getDate());
    };

    public static String getName(TagNode tagNode) {
        return Arrays
                .stream(tagNode.getElementsByAttValue(CLASS, "ReportsTable__reportNameText",
                        true, false)).findFirst()
                .map(at -> at.getAttributeByName("title"))
                .orElseThrow(() -> new IllegalArgumentException("can not find title"));
    }

}
