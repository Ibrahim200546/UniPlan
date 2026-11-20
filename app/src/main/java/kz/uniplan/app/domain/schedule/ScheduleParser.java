package kz.uniplan.app.domain.schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import kz.uniplan.app.domain.schedule.ScheduleImport.Entry;
import kz.uniplan.app.domain.schedule.ScheduleImport.Issue;
import kz.uniplan.app.domain.schedule.ScheduleImport.Preview;

/** Markdown, spreadsheet TSV/CSV, and real HTML tables (including merged day cells). */
public final class ScheduleParser {
    private static final Pattern TIME=Pattern.compile("^(\\d{1,2})[:.](\\d{2})\\s*[-–—−]\\s*(\\d{1,2})[:.](\\d{2})$");
    private static final Pattern LINK=Pattern.compile("\\[[^\\]]*]\\((https?://[^\\s]+)\\)");
    private static final String[] FIELDS={"day","number","time","subject","type","teacher","room","url","access"};
    private ScheduleParser() { }
    public static Preview parse(String input){return parse(input,"");}
    public static Preview parse(String input,String baseUrl) {
        if(input==null||input.trim().isEmpty())return error("empty");
        if(input.length()>ScheduleImport.MAX_INPUT)return error("limit");
        try {
            if(Pattern.compile("<(table|tr)\\b",Pattern.CASE_INSENSITIVE).matcher(input).find()) {
                Document doc=Jsoup.parse(input,baseUrl);
                List<Entry> entries=new ArrayList<>();List<Issue> issues=new ArrayList<>();int access=0;
                for(Element table:doc.select("table")) {
                    Preview part=read(htmlRows(table));
                    if(part.entries.isEmpty()&&part.issues.size()==1&&"header".equals(part.issues.get(0).code))continue;
                    entries.addAll(part.entries);issues.addAll(part.issues);access+=part.ignoredAccessFields;
                }
                if(entries.isEmpty()&&issues.isEmpty())return error("header");
                if(entries.size()>ScheduleImport.MAX_ROWS)return error("limit");
                return new Preview(entries,issues,access);
            }
            return read(textRows(input));
        } catch(IllegalArgumentException malformed){return error("format");}
    }
    private static Preview error(String code){return new Preview(new ArrayList<>(),Arrays.asList(new Issue(0,code)),0);}
    private static Preview read(List<List<String>> rows) {
        List<Entry> entries=new ArrayList<>();List<Issue> issues=new ArrayList<>();
        Map<String,Integer> columns=null;int previousDay=0,access=0,rowNumber=0;
        for(List<String> cells:rows) {
            rowNumber++;
            if(cells.isEmpty()||cells.stream().allMatch(s->s.trim().isEmpty()||s.trim().matches(":?-{2,}:?")))continue;
            Map<String,Integer> header=header(cells);
            if(header.containsKey("day")&&header.containsKey("time")&&header.containsKey("subject")){columns=header;previousDay=0;continue;}
            if(columns==null) {
                int offset=cells.size()>=8&&day(cells.get(0))>0?0:cells.size()>=9&&day(cells.get(1))>0?1:-1;
                if(offset<0)continue;
                columns=new HashMap<>();for(int i=0;i<FIELDS.length&&i+offset<cells.size();i++)columns.put(FIELDS[i],i+offset);
            }
            if(entries.size()+issues.size()>=ScheduleImport.MAX_ROWS)return error("limit");
            String dayText=get(cells,columns,"day");
            int day=dayText.isEmpty()?previousDay:day(dayText);
            if(day>0)previousDay=day;
            Matcher time=TIME.matcher(get(cells,columns,"time"));
            if(day==0){issues.add(new Issue(rowNumber,"day"));continue;}
            if(!time.matches()){issues.add(new Issue(rowNumber,"time"));continue;}
            int h1=Integer.parseInt(time.group(1)),m1=Integer.parseInt(time.group(2)),h2=Integer.parseInt(time.group(3)),m2=Integer.parseInt(time.group(4));
            if(h1>23||h2>23||m1>59||m2>59||h1*60+m1>=h2*60+m2){issues.add(new Issue(rowNumber,"time"));continue;}
            String number=get(cells,columns,"number");
            if(!number.isEmpty()&&!number.matches("[0-9]{1,2}")){issues.add(new Issue(rowNumber,"number"));continue;}
            Entry entry=new Entry(rowNumber,day,number.isEmpty()?0:Integer.parseInt(number),h1*60+m1,h2*60+m2,
                    get(cells,columns,"subject").replaceAll("\\s+"," "),get(cells,columns,"type"),
                    get(cells,columns,"teacher"),get(cells,columns,"room"),get(cells,columns,"url"));
            if(!entry.valid()){issues.add(new Issue(rowNumber,"fields"));continue;}
            entries.add(entry);
            if(!get(cells,columns,"access").isEmpty())access++;
        }
        if(columns==null)return error("header");
        if(entries.isEmpty()&&issues.isEmpty())return error("empty");
        return new Preview(entries,issues,access);
    }
    private static String get(List<String> cells,Map<String,Integer> columns,String field) {
        Integer index=columns.get(field);return index==null||index>=cells.size()?"":clean(cells.get(index));
    }
    private static String clean(String text) {
        String value=LINK.matcher(text).replaceAll("$1").replaceAll("(?i)<br\\s*/?>","\n");
        if(value.indexOf('<')>=0)value=Jsoup.parseBodyFragment(value).body().wholeText();
        return org.jsoup.parser.Parser.unescapeEntities(value,false).replace('\u00a0',' ').replace("\r","")
                .replaceAll("[\\t ]+"," ").replaceAll(" *\n *","\n").trim();
    }
    private static Map<String,Integer> header(List<String> cells) {
        Map<String,Integer> result=new HashMap<>();
        for(int i=0;i<cells.size();i++) {
            String s=ScheduleImport.key(clean(cells.get(i))).replaceAll("[^\\p{L}]","");String field=null;
            switch(s) {
                case "день":case "деньнедели":case "күн":case "аптакүні":case "day":field="day";break;
                case "занятие":case "пара":case "сабақ":case "сабақнөмірі":case "lesson":field="number";break;
                case "время":case "уақыт":case "уақыты":case "time":field="time";break;
                case "наименованиедисциплины":case "дисциплина":case "предмет":case "пән":case "пәнатауы":case "subject":case "course":field="subject";break;
                case "видзанятия":case "сабақтүрі":case "type":field="type";break;
                case "фиопреподавателя":case "преподаватель":case "оқытушы":case "оқытушыныңатыжөні":case "teacher":field="teacher";break;
                case "аудитория":case "бөлме":case "кабинет":case "room":field="room";break;
                case "ссылкавкс":case "сілтеме":case "вкссілтемесі":case "link":case "url":field="url";break;
                case "логинпарольвкс":case "логинпароль":field="access";break;
            }
            if(field!=null)result.put(field,i);
        }
        return result;
    }
    static int day(String value) {
        switch(ScheduleImport.key(clean(value)).replace(".","")) {
            case "пн":case "дс":case "дүйсенбі":case "понедельник":case "mon":case "monday":return 1;
            case "вт":case "сс":case "сейсенбі":case "вторник":case "tue":case "tuesday":return 2;
            case "ср":case "сәрсенбі":case "среда":case "wed":case "wednesday":return 3;
            case "чт":case "бс":case "бейсенбі":case "четверг":case "thu":case "thursday":return 4;
            case "пт":case "жм":case "жұма":case "пятница":case "fri":case "friday":return 5;
            case "сб":case "сенбі":case "суббота":case "sat":case "saturday":return 6;
            case "вс":case "жс":case "жексенбі":case "воскресенье":case "sun":case "sunday":return 7;
            default:return 0;
        }
    }
    private static List<List<String>> textRows(String text) {
        List<List<String>> rows=new ArrayList<>();
        boolean markdown=Arrays.stream(text.split("\\R")).anyMatch(line->line.trim().startsWith("|"))||!text.contains("\t")&&text.contains("|");
        if(markdown) {
            for(String line:text.split("\\R")) {
                if(!line.contains("|"))continue;
                String stripped=line.trim();if(stripped.startsWith("|"))stripped=stripped.substring(1);if(stripped.endsWith("|"))stripped=stripped.substring(0,stripped.length()-1);
                rows.add(Arrays.asList(stripped.split("(?<!\\\\)\\|",-1)));
            }
        } else {
            char delimiter=text.contains("\t")?'\t':';';
            List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
            for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                if(c=='"'&&(quoted||cell.length()==0)) {
                    if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;
                } else if(!quoted&&(c==delimiter||c=='\n')) {
                    row.add(cell.toString());cell.setLength(0);
                    if(c=='\n'){rows.add(row);row=new ArrayList<>();}
                } else if(c!='\r')cell.append(c);
            }
            if(quoted)throw new IllegalArgumentException("Unclosed quote");
            row.add(cell.toString());rows.add(row);
        }
        return rows;
    }
    private static final class Span { final String text;int rows;Span(String text,int rows){this.text=text;this.rows=rows;} }
    private static List<List<String>> htmlRows(Element table) {
        List<List<String>> result=new ArrayList<>();Map<Integer,Span> spans=new HashMap<>();
        for(Element tr:table.select("tr")) {
            Element parent=tr.parent();while(parent!=null&&!parent.tagName().equals("table"))parent=parent.parent();
            if(parent!=table)continue;
            if(result.size()>ScheduleImport.MAX_ROWS+5)throw new IllegalArgumentException("Too many rows");
            Map<Integer,String> values=new HashMap<>();
            for(Integer col:new ArrayList<>(spans.keySet())){Span span=spans.get(col);values.put(col,span.text);if(--span.rows==0)spans.remove(col);}
            int col=0;
            for(Element cell:tr.children()) {
                if(!cell.tagName().equals("td")&&!cell.tagName().equals("th"))continue;
                while(values.containsKey(col))col++;
                int width=span(cell,"colspan",32),height=span(cell,"rowspan",ScheduleImport.MAX_ROWS);
                Element copy=cell.clone();
                for(Element link:copy.select("a[href]")) {String url=link.absUrl("href");if(url.isEmpty())url=link.attr("href");link.replaceWith(new TextNode(url));}
                for(Element br:copy.select("br"))br.replaceWith(new TextNode("\n"));
                String value=copy.wholeText();
                for(int j=0;j<width;j++){if(col>=32)throw new IllegalArgumentException("Too many columns");values.put(col,value);if(height>1)spans.put(col,new Span(value,height-1));col++;}
            }
            List<String> row=new ArrayList<>();int max=values.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
            for(int i=0;i<=max;i++)row.add(values.getOrDefault(i,""));result.add(row);
        }
        return result;
    }
    private static int span(Element cell,String name,int max) {
        String value=cell.attr(name);if(value.isEmpty())return 1;
        try {int count=Integer.parseInt(value);if(count<1||count>max)throw new IllegalArgumentException("Invalid span");return count;}
        catch(NumberFormatException bad){throw new IllegalArgumentException("Invalid span",bad);}
    }
}
