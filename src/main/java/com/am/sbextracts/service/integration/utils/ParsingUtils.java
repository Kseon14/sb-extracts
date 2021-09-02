package com.am.sbextracts.service.integration.utils;

import feign.Response;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParsingUtils {

    public static String getInn(TagNode tag) {
        return StringUtils.strip(((ContentNode) tag.getAllChildren().get(0)).getContent()).split("\\.")[1];
    }

    public static String getFileTitle(TagNode tag) {
        return StringUtils.strip(((ContentNode) tag.getAllChildren().get(0)).getContent());
    }

    // previewFile('24976', '24980');
    public static int getFileId(TagNode tag) {
        return getItem(tag, 1);
    }

    public static int getItem(TagNode tag, int index) {
        final Pattern pattern = Pattern.compile("'(.*?)'");
        final Matcher matcher = pattern.matcher(tag.getAttributes().get("onclick"));
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

    @SneakyThrows
    public static TagNode getTagNode(Response.Body body) {
        return new HtmlCleaner().clean(body.asInputStream());
    }


    public static boolean isSigned(TagNode tagNode) {
        ContentNode node = (ContentNode) tagNode.getParent().getElementsByAttValue("class", "ReportsTable__statusIcon",
                true, false)[0].getParent().getAllChildren().get(2);
        return StringUtils.equals(node.getContent(), "1");
    }

    public static boolean isAktAndDate(TagNode tagNode, String date) {
        Optional<String> title = Arrays
                .stream(tagNode.getElementsByAttValue("class", "ReportsTable__reportNameText", true, false)).findFirst()
                .map(at -> at.getAttributeByName("title"));
        String[] splitResult = title.map(t -> t.split("\\."))
                .orElseThrow(() -> new IllegalArgumentException("can not filter by akt and date"));
        if (splitResult.length < 6) {
            return false;
        }
        return splitResult[5].equals("akt")
                && String.join(".", splitResult[2], splitResult[3], splitResult[4]).equals(date);
    }

    public static final Predicate<TagNode> isRequiredTag = (tagNode) -> tagNode.getAttributes().containsKey("onclick")
            && tagNode.getAttributeByName("onclick").startsWith("previewFile");

    // documents with sverka word in name, that indicate different structure
    public static final Predicate<TagNode> isAkt = (tagNode) -> {
        String contentNodeContent = StringUtils.strip(((ContentNode) tagNode.getAllChildren().get(0)).getContent());
        String[] splitContent = contentNodeContent.split("\\.");
        return CollectionUtils.containsAny(Arrays.asList(splitContent), "akt");
    };

    public static String getName(TagNode tagNode){
        return Arrays
                .stream(tagNode.getElementsByAttValue("class", "ReportsTable__reportNameText", true, false)).findFirst()
                .map(at -> at.getAttributeByName("title"))
                .orElseThrow(() -> new IllegalArgumentException("can find title"));
    }

}
