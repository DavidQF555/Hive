package hive.common.world.entities.ai;

public class IntegerAABB {

    protected int minX, minY, minZ, maxX, maxY, maxZ;

    public IntegerAABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
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
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
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
