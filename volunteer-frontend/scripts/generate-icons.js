const fs = require('fs');
const path = require('path');

const tabbarDir = path.join(__dirname, '../static/tabbar');

if (!fs.existsSync(tabbarDir)) {
  fs.mkdirSync(tabbarDir, { recursive: true });
}

const createSimplePNG = (width, height, r, g, b, a = 255) => {
  const signature = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
  
  const createChunk = (type, data) => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const typeBuffer = Buffer.from(type);
    const crcData = Buffer.concat([typeBuffer, data]);
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < crcData.length; i++) {
      crc ^= crcData[i];
      for (let j = 0; j < 8; j++) {
        crc = (crc >>> 1) ^ (crc & 1 ? 0xEDB88320 : 0);
      }
    }
    crc = (crc ^ 0xFFFFFFFF) >>> 0;
    const crcBuffer = Buffer.alloc(4);
    crcBuffer.writeUInt32BE(crc);
    return Buffer.concat([length, typeBuffer, data, crcBuffer]);
  };
  
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;
  
  const rawData = [];
  for (let y = 0; y < height; y++) {
    rawData.push(0);
    for (let x = 0; x < width; x++) {
      rawData.push(r, g, b, a);
    }
  }
  
  const { deflateSync } = require('zlib');
  const compressed = deflateSync(Buffer.from(rawData));
  
  const ihdrChunk = createChunk('IHDR', ihdr);
  const idatChunk = createChunk('IDAT', compressed);
  const iendChunk = createChunk('IEND', Buffer.alloc(0));
  
  return Buffer.concat([signature, ihdrChunk, idatChunk, iendChunk]);
};

const icons = [
  { name: 'activity', color: [153, 153, 153], activeColor: [102, 126, 234] },
  { name: 'activity-active', color: [102, 126, 234], activeColor: [102, 126, 234] },
  { name: 'todo', color: [153, 153, 153], activeColor: [102, 126, 234] },
  { name: 'todo-active', color: [102, 126, 234], activeColor: [102, 126, 234] },
  { name: 'message', color: [153, 153, 153], activeColor: [102, 126, 234] },
  { name: 'message-active', color: [102, 126, 234], activeColor: [102, 126, 234] },
  { name: 'tools', color: [153, 153, 153], activeColor: [102, 126, 234] },
  { name: 'tools-active', color: [102, 126, 234], activeColor: [102, 126, 234] },
  { name: 'mine', color: [153, 153, 153], activeColor: [102, 126, 234] },
  { name: 'mine-active', color: [102, 126, 234], activeColor: [102, 126, 234] }
];

icons.forEach(icon => {
  const png = createSimplePNG(48, 48, icon.color[0], icon.color[1], icon.color[2]);
  fs.writeFileSync(path.join(tabbarDir, `${icon.name}.png`), png);
  console.log(`Created ${icon.name}.png`);
});

console.log('All icons generated successfully!');