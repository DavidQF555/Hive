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

    public IntegerAABB[] truncate(IntegerAABB bounds) {
        if (!intersects(bounds)) {
            return new IntegerAABB[]{this};
        }
        IntegerAABB[] arr = new IntegerAABB[6];
        int i = 0;
        int minY, maxY;
        if (bounds.maxY < this.maxY) {
            maxY = bounds.maxY;
            IntegerAABB top = new IntegerAABB(this.minX, bounds.maxY, this.minZ, this.maxX, this.maxY, this.maxZ);
            arr[i++] = top;
        } else {
            maxY = this.maxY;
        }
        if (bounds.minY > this.minY) {
            minY = bounds.minY;
            IntegerAABB bot = new IntegerAABB(this.minX, this.minY, this.minZ, this.maxX, bounds.minY, this.maxZ);
            arr[i++] = bot;
        } else {
            minY = this.minY;
        }
        int minX, maxX;
        if (bounds.maxX < this.maxX) {
            maxX = bounds.maxX;
            IntegerAABB pX = new IntegerAABB(bounds.maxX, minY, this.minZ, this.maxZ, maxY, this.maxZ);
            arr[i++] = pX;
        } else {
            maxX = this.maxX;
        }
        if (bounds.minX > this.minX) {
            minX = bounds.minX;
            IntegerAABB nX = new IntegerAABB(this.minX, minY, this.minZ, bounds.minX, maxY, this.maxZ);
            arr[i++] = nX;
        } else {
            minX = this.minX;
        }
        if (bounds.maxZ < this.maxZ) {
            IntegerAABB pZ = new IntegerAABB(minX, minY, bounds.maxZ, maxX, maxY, this.maxZ);
            arr[i++] = pZ;
        }
        if (bounds.minX > this.minX) {
            IntegerAABB nX = new IntegerAABB(minX, minY, this.minZ, maxX, maxY, bounds.minZ);
            arr[i] = nX;
        }
        return arr;
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
