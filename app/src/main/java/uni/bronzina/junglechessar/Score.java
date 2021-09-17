package uni.bronzina.junglechessar;

import android.os.Parcel;
import android.os.Parcelable;

public class Score implements Parcelable {

    protected Integer index;
    protected Long time;

    public Score(Integer index, Long time){
        this.index = index;
        this.time = time;
    }

    public Score(Parcel in) {
        super();
        readFromParcel(in);
    }

    public static final Parcelable.Creator<Score> CREATOR = new Parcelable.Creator<Score>() {
        public Score createFromParcel(Parcel in) {
            return new Score(in);
        }

        public Score[] newArray(int size) {

            return new Score[size];
        }

    };

    public void readFromParcel(Parcel in) {
        index = in.readInt();
        time = in.readLong();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(index);
        dest.writeLong(time);
    }

}
