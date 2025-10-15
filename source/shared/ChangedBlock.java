package shared;

import java.io.Serializable;

/**
 * Lớp này đại diện cho một khối ảnh đã thay đổi.
 * Nó phải implements Serializable để có thể được gửi qua ObjectOutputStream.
 */
public class ChangedBlock implements Serializable {
    // serialVersionUID là cần thiết để đảm bảo tính tương thích khi deserialization.
    private static final long serialVersionUID = 7526472295622776147L;

    public final int x;
    public final int y;
    public final byte[] imageData; // Dữ liệu ảnh của khối đã được nén (PNG)

    public ChangedBlock(int x, int y, byte[] imageData) {
        this.x = x;
        this.y = y;
        this.imageData = imageData;
    }
}