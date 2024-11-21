package io.github.toribane.kkbd;

public class Word implements Comparable<Word> {

    // surfaceは','を含む場合があるので末尾
    // costは切り分けしやすいように先頭

    public short cost;
    public short lid;
    public short rid;
    public String surface;

    public Word(short cost, short lid, short rid, String surface) {
        this.cost = cost;
        this.lid = lid;
        this.rid = rid;
        this.surface = surface;
    }

    public Word(String s) {
        String[] ss = s.split(",", 4);
        this.cost = Short.parseShort(ss[0]);
        this.lid = Short.parseShort(ss[1]);
        this.rid = Short.parseShort(ss[2]);
        this.surface = ss[3];
    }

    @Override
    public String toString() {
        return cost + "," + lid + "," + rid + "," + surface;
    }

    public int getCost() {
        return (int) cost;
    }

    @Override
    public int compareTo(Word word) {
        if (cost != word.cost) {
            return (int) (cost - word.cost);
        }
        return surface.compareTo(word.surface);
    }

}
