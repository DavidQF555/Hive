package hive.common.world.entities.ai;

public class IntegerAABB {

    protected int minX, minY, minZ, maxX, maxY, maxZ;

    public IntegerAABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public IntegerAABB immutable() {
        return this;
    }

    protected void set(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public boolean isEmpty() {
        return maxX <= minX || maxY <= minY || maxZ <= minZ;
    }

    public boolean intersects(IntegerAABB bounds) {
        return bounds.maxX > minX && bounds.maxY > minY && bounds.maxZ > minZ && maxX > bounds.minX && maxY > bounds.minY && maxZ > bounds.minZ;
    }

    public boolean intersects(int x, int y, int z) {
        return minX <= x && maxX > x && minY <= y && maxY > y && minZ <= z && maxZ > z;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IntegerAABB bounds && bounds.minX == minX && bounds.maxX == maxX && bounds.minY == minY && bounds.maxY == maxY && bounds.minZ == minZ && bounds.maxZ == maxZ;
    }

    // Could use better hash function
    @Override
    public int hashCode() {
        return (minX * maxX) + (minY * maxY) + (minZ * maxZ);
    }

    public static class Mutable extends IntegerAABB {

        public Mutable() {
            super(0, 0, 0, 0, 0, 0);
        }

        @Override
        public void set(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            super.set(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public IntegerAABB immutable() {
            return new IntegerAABB(getMinX(), getMinY(), getMinZ(), getMaxX(), getMaxY(), getMaxZ());
        }

    }

}
