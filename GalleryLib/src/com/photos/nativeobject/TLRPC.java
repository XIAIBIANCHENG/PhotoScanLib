package com.photos.nativeobject;

import java.util.ArrayList;

public class TLRPC {
	public static class FileLocation extends TLObject {
		public int dc_id;
		public long volume_id;
		public int local_id;
        public long secret;
		public byte[] key;
        public byte[] iv;

		public static FileLocation TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
            FileLocation result = null;
            switch (constructor) {
				case 0x53d69076:
					result = new TL_fileLocation();
					break;
				case 0x55555554:
					result = new TL_fileEncryptedLocation();
					break;
				case 0x7c596b46:
					result = new TL_fileLocationUnavailable();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in FileLocation", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}
	
	public static class TL_fileLocation extends FileLocation {
		public static int constructor = 0x53d69076;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			dc_id = stream.readInt32(exception);
			volume_id = stream.readInt64(exception);
			local_id = stream.readInt32(exception);
			secret = stream.readInt64(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt32(dc_id);
			stream.writeInt64(volume_id);
			stream.writeInt32(local_id);
			stream.writeInt64(secret);
		}
	}
	
	public static class TL_fileEncryptedLocation extends FileLocation {
		public static int constructor = 0x55555554;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			dc_id = stream.readInt32(exception);
			volume_id = stream.readInt64(exception);
            local_id = stream.readInt32(exception);
            secret = stream.readInt64(exception);
			key = stream.readByteArray(exception);
			iv = stream.readByteArray(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
			stream.writeInt32(dc_id);
			stream.writeInt64(volume_id);
            stream.writeInt32(local_id);
            stream.writeInt64(secret);
			stream.writeByteArray(key);
			stream.writeByteArray(iv);
		}
	}

	public static class TL_fileLocationUnavailable extends FileLocation {
		public static int constructor = 0x7c596b46;


		public void readParams(AbstractSerializedData stream, boolean exception) {
            volume_id = stream.readInt64(exception);
			local_id = stream.readInt32(exception);
			secret = stream.readInt64(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(volume_id);
			stream.writeInt32(local_id);
			stream.writeInt64(secret);
		}
	}
	
	public static class TL_photoSize extends PhotoSize {
		public static int constructor = 0x77bfb61b;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			type = stream.readString(exception);
			location = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
			w = stream.readInt32(exception);
			h = stream.readInt32(exception);
			size = stream.readInt32(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeString(type);
			location.serializeToStream(stream);
			stream.writeInt32(w);
			stream.writeInt32(h);
			stream.writeInt32(size);
		}
	}

	public static class TL_photoSizeEmpty extends PhotoSize {
		public static int constructor = 0xe17e23c;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			int startReadPosiition = stream.getPosition(); //TODO remove this hack after some time
			try {
				type = stream.readString(true);
				if (type.length() > 1 || !type.equals("") && !type.equals("s") && !type.equals("x") && !type.equals("m") && !type.equals("y") && !type.equals("w")) {
					type = "s";
					if (stream instanceof NativeByteBuffer) {
						((NativeByteBuffer) stream).position(startReadPosiition);
					}
				}
			} catch (Exception e) {
				type = "s";
				if (stream instanceof NativeByteBuffer) {
					((NativeByteBuffer) stream).position(startReadPosiition);
				}
			}
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeString(type);
		}
	}

	public static class TL_photoCachedSize extends PhotoSize {
		public static int constructor = 0xe9a734fa;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			type = stream.readString(exception);
			location = FileLocation.TLdeserialize(stream, stream.readInt32(exception), exception);
			w = stream.readInt32(exception);
			h = stream.readInt32(exception);
			bytes = stream.readByteArray(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeString(type);
			location.serializeToStream(stream);
            stream.writeInt32(w);
			stream.writeInt32(h);
            stream.writeByteArray(bytes);
		}
	}
	
	public static class PhotoSize extends TLObject {
		public String type;
		public FileLocation location;
		public int w;
		public int h;
		public int size;
		public byte[] bytes;

		public static PhotoSize TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			PhotoSize result = null;
			switch(constructor) {
				case 0x77bfb61b:
					result = new TL_photoSize();
					break;
				case 0xe17e23c:
					result = new TL_photoSizeEmpty();
					break;
				case 0xe9a734fa:
					result = new TL_photoCachedSize();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in PhotoSize", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}
	
	public static class TL_inputStickerSetEmpty extends InputStickerSet {
		public static int constructor = 0xffb62b95;


		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
		}
	}

	public static class TL_inputStickerSetID extends InputStickerSet {
		public static int constructor = 0x9de7a269;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			access_hash = stream.readInt64(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt64(access_hash);
		}
	}

	public static class TL_inputStickerSetShortName extends InputStickerSet {
		public static int constructor = 0x861cc8a0;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			short_name = stream.readString(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeString(short_name);
		}
	}
	
	public static class InputStickerSet extends TLObject {
		public long id;
		public long access_hash;
		public String short_name;

		public static InputStickerSet TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			InputStickerSet result = null;
			switch(constructor) {
				case 0xffb62b95:
					result = new TL_inputStickerSetEmpty();
					break;
				case 0x9de7a269:
					result = new TL_inputStickerSetID();
					break;
				case 0x861cc8a0:
					result = new TL_inputStickerSetShortName();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in InputStickerSet", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}
	
	public static class TL_documentAttributeAnimated extends DocumentAttribute {
		public static int constructor = 0x11b58939;


		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
		}
	}

	public static class TL_documentAttributeSticker_old extends TL_documentAttributeSticker {
		public static int constructor = 0xfb0a5727;


		public void readParams(AbstractSerializedData stream, boolean exception) {
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
		}
	}

	public static class TL_documentAttributeImageSize extends DocumentAttribute {
		public static int constructor = 0x6c37c15c;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			w = stream.readInt32(exception);
			h = stream.readInt32(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt32(w);
			stream.writeInt32(h);
		}
	}

	public static class TL_documentAttributeAudio_old extends TL_documentAttributeAudio {
		public static int constructor = 0x51448e5;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			duration = stream.readInt32(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt32(duration);
		}
	}

	public static class TL_documentAttributeSticker extends DocumentAttribute {
		public static int constructor = 0x3a556302;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			alt = stream.readString(exception);
			stickerset = InputStickerSet.TLdeserialize(stream, stream.readInt32(exception), exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeString(alt);
			stickerset.serializeToStream(stream);
		}
	}

	public static class TL_documentAttributeVideo extends DocumentAttribute {
		public static int constructor = 0x5910cccb;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			duration = stream.readInt32(exception);
			w = stream.readInt32(exception);
			h = stream.readInt32(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt32(duration);
			stream.writeInt32(w);
			stream.writeInt32(h);
		}
	}

	public static class TL_documentAttributeAudio extends DocumentAttribute {
		public static int constructor = 0x9852f9c6;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			flags = stream.readInt32(exception);
			voice = (flags & 1024) != 0;
			duration = stream.readInt32(exception);
			if ((flags & 1) != 0) {
				title = stream.readString(exception);
			}
			if ((flags & 2) != 0) {
				performer = stream.readString(exception);
			}
			if ((flags & 4) != 0) {
				waveform = stream.readByteArray(exception);
			}
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			flags = voice ? (flags | 1024) : (flags &~ 1024);
			stream.writeInt32(flags);
			stream.writeInt32(duration);
			if ((flags & 1) != 0) {
				stream.writeString(title);
			}
			if ((flags & 2) != 0) {
				stream.writeString(performer);
			}
			if ((flags & 4) != 0) {
				stream.writeByteArray(waveform);
			}
		}
	}

	public static class TL_documentAttributeSticker_old2 extends TL_documentAttributeSticker {
		public static int constructor = 0x994c9882;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			alt = stream.readString(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeString(alt);
		}
	}

	public static class TL_documentAttributeFilename extends DocumentAttribute {
		public static int constructor = 0x15590068;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			file_name = stream.readString(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeString(file_name);
		}
	}

	public static class TL_documentAttributeAudio_layer45 extends TL_documentAttributeAudio {
		public static int constructor = 0xded218e0;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			duration = stream.readInt32(exception);
			title = stream.readString(exception);
			performer = stream.readString(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt32(duration);
			stream.writeString(title);
			stream.writeString(performer);
		}
	}
	
	public static class DocumentAttribute extends TLObject {
		public int w;
		public int h;
		public int duration;
		public String alt;
		public InputStickerSet stickerset;
		public int flags;
		public boolean voice;
		public String title;
		public String performer;
		public byte[] waveform;
		public String file_name;

		public static DocumentAttribute TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			DocumentAttribute result = null;
			switch(constructor) {
				case 0x11b58939:
					result = new TL_documentAttributeAnimated();
					break;
				case 0xfb0a5727:
					result = new TL_documentAttributeSticker_old();
					break;
				case 0x6c37c15c:
					result = new TL_documentAttributeImageSize();
					break;
				case 0x51448e5:
					result = new TL_documentAttributeAudio_old();
					break;
				case 0x3a556302:
					result = new TL_documentAttributeSticker();
					break;
				case 0x5910cccb:
					result = new TL_documentAttributeVideo();
					break;
				case 0x9852f9c6:
					result = new TL_documentAttributeAudio();
					break;
				case 0x994c9882:
					result = new TL_documentAttributeSticker_old2();
					break;
				case 0x15590068:
					result = new TL_documentAttributeFilename();
					break;
				case 0xded218e0:
					result = new TL_documentAttributeAudio_layer45();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in DocumentAttribute", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}
	
	public static class TL_documentEncrypted_old extends TL_document {
		public static int constructor = 0x55555556;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			access_hash = stream.readInt64(exception);
            user_id = stream.readInt32(exception);
			date = stream.readInt32(exception);
			file_name = stream.readString(exception);
			mime_type = stream.readString(exception);
			size = stream.readInt32(exception);
			thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
			dc_id = stream.readInt32(exception);
            key = stream.readByteArray(exception);
			iv = stream.readByteArray(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt64(access_hash);
			stream.writeInt32(user_id);
			stream.writeInt32(date);
			stream.writeString(file_name);
			stream.writeString(mime_type);
			stream.writeInt32(size);
			thumb.serializeToStream(stream);
			stream.writeInt32(dc_id);
			stream.writeByteArray(key);
			stream.writeByteArray(iv);
		}
	}

	public static class TL_document_old extends TL_document {
		public static int constructor = 0x9efc6326;


		public void readParams(AbstractSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            access_hash = stream.readInt64(exception);
			user_id = stream.readInt32(exception);
			date = stream.readInt32(exception);
            file_name = stream.readString(exception);
			mime_type = stream.readString(exception);
            size = stream.readInt32(exception);
            thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
			dc_id = stream.readInt32(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
            stream.writeInt64(access_hash);
            stream.writeInt32(user_id);
            stream.writeInt32(date);
			stream.writeString(file_name);
            stream.writeString(mime_type);
            stream.writeInt32(size);
			thumb.serializeToStream(stream);
			stream.writeInt32(dc_id);
		}
	}

	public static class TL_documentEmpty extends Document {
		public static int constructor = 0x36f8c871;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
		}

        public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
		}
	}

	public static class TL_documentEncrypted extends Document {
		public static int constructor = 0x55555558;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			access_hash = stream.readInt64(exception);
			date = stream.readInt32(exception);
			int startReadPosiition = stream.getPosition(); //TODO remove this hack after some time
			try {
				mime_type = stream.readString(true);
			} catch (Exception e) {
				mime_type = "audio/ogg";
				if (stream instanceof NativeByteBuffer) {
					((NativeByteBuffer) stream).position(startReadPosiition);
				}
			}
			size = stream.readInt32(exception);
			thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
			dc_id = stream.readInt32(exception);
			int magic = stream.readInt32(exception);
			if (magic != 0x1cb5c415) {
				if (exception) {
					throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
				}
				return;
			}
			int count = stream.readInt32(exception);
			for (int a = 0; a < count; a++) {
				DocumentAttribute object = DocumentAttribute.TLdeserialize(stream, stream.readInt32(exception), exception);
				if (object == null) {
					return;
				}
				attributes.add(object);
			}
			key = stream.readByteArray(exception);
			iv = stream.readByteArray(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt64(access_hash);
			stream.writeInt32(date);
			stream.writeString(mime_type);
			stream.writeInt32(size);
			thumb.serializeToStream(stream);
			stream.writeInt32(dc_id);
			stream.writeInt32(0x1cb5c415);
			int count = attributes.size();
			stream.writeInt32(count);
			for (int a = 0; a < count; a++) {
				attributes.get(a).serializeToStream(stream);
			}
			stream.writeByteArray(key);
			stream.writeByteArray(iv);
		}
	}

	public static class TL_document extends Document {
		public static int constructor = 0xf9a39f4f;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			access_hash = stream.readInt64(exception);
			date = stream.readInt32(exception);
			mime_type = stream.readString(exception);
			size = stream.readInt32(exception);
			thumb = PhotoSize.TLdeserialize(stream, stream.readInt32(exception), exception);
			dc_id = stream.readInt32(exception);
			int magic = stream.readInt32(exception);
			if (magic != 0x1cb5c415) {
				if (exception) {
					throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
				}
				return;
			}
			int count = stream.readInt32(exception);
			for (int a = 0; a < count; a++) {
				DocumentAttribute object = DocumentAttribute.TLdeserialize(stream, stream.readInt32(exception), exception);
				if (object == null) {
					return;
				}
				attributes.add(object);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
			stream.writeInt64(id);
            stream.writeInt64(access_hash);
			stream.writeInt32(date);
            stream.writeString(mime_type);
			stream.writeInt32(size);
            thumb.serializeToStream(stream);
			stream.writeInt32(dc_id);
            stream.writeInt32(0x1cb5c415);
			int count = attributes.size();
			stream.writeInt32(count);
			for (int a = 0; a < count; a++) {
				attributes.get(a).serializeToStream(stream);
			}
		}
	}
	public static class Document extends TLObject {
		public long id;
		public long access_hash;
		public int user_id;
		public int date;
		public String file_name;
		public String mime_type;
		public int size;
		public PhotoSize thumb;
		public int dc_id;
		public byte[] key;
		public byte[] iv;
		public String caption;
		public ArrayList<DocumentAttribute> attributes = new ArrayList<DocumentAttribute>();

		public static Document TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			Document result = null;
			switch(constructor) {
				case 0x55555556:
					result = new TL_documentEncrypted_old();
					break;
				case 0x9efc6326:
					result = new TL_document_old();
					break;
				case 0x36f8c871:
					result = new TL_documentEmpty();
					break;
				case 0x55555558:
					result = new TL_documentEncrypted();
					break;
				case 0xf9a39f4f:
					result = new TL_document();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in Document", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}
	
	public static class InputFile extends TLObject {
		public long id;
		public int parts;
		public String name;
		public String md5_checksum;

		public static InputFile TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			InputFile result = null;
			switch(constructor) {
				case 0xfa4f0bb5:
					result = new TL_inputFileBig();
					break;
				case 0xf52ff27f:
					result = new TL_inputFile();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in InputFile", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}

	public static class TL_inputFileBig extends InputFile {
		public static int constructor = 0xfa4f0bb5;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			parts = stream.readInt32(exception);
			name = stream.readString(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt32(parts);
			stream.writeString(name);
		}
	}

	public static class TL_inputFile extends InputFile {
		public static int constructor = 0xf52ff27f;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			parts = stream.readInt32(exception);
			name = stream.readString(exception);
			md5_checksum = stream.readString(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt32(parts);
			stream.writeString(name);
			stream.writeString(md5_checksum);
		}
	}
	
	public static class InputEncryptedFile extends TLObject {
		public long id;
		public long access_hash;
		public int parts;
		public int key_fingerprint;
		public String md5_checksum;

        public static InputEncryptedFile TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			InputEncryptedFile result = null;
			switch(constructor) {
				case 0x5a17b5e5:
					result = new TL_inputEncryptedFile();
					break;
				case 0x2dc173c8:
					result = new TL_inputEncryptedFileBigUploaded();
					break;
				case 0x1837c364:
					result = new TL_inputEncryptedFileEmpty();
                    break;
				case 0x64bd0306:
					result = new TL_inputEncryptedFileUploaded();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in InputEncryptedFile", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}

	public static class TL_inputEncryptedFile extends InputEncryptedFile {
		public static int constructor = 0x5a17b5e5;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			access_hash = stream.readInt64(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt64(access_hash);
		}
	}

	public static class TL_inputEncryptedFileBigUploaded extends InputEncryptedFile {
		public static int constructor = 0x2dc173c8;


		public void readParams(AbstractSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            parts = stream.readInt32(exception);
			key_fingerprint = stream.readInt32(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
			stream.writeInt32(parts);
			stream.writeInt32(key_fingerprint);
		}
	}

	public static class TL_inputEncryptedFileEmpty extends InputEncryptedFile {
		public static int constructor = 0x1837c364;


		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
		}
	}

	public static class TL_inputEncryptedFileUploaded extends InputEncryptedFile {
		public static int constructor = 0x64bd0306;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			parts = stream.readInt32(exception);
			md5_checksum = stream.readString(exception);
			key_fingerprint = stream.readInt32(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt32(parts);
			stream.writeString(md5_checksum);
			stream.writeInt32(key_fingerprint);
		}
	}
	
	public static class TL_error extends TLObject {
		public static int constructor = 0xc4b9f9bb;

		public int code;
		public String text;

		public static TL_error TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			if (TL_error.constructor != constructor) {
				if (exception) {
					throw new RuntimeException(String.format("can't parse magic %x in TL_error", constructor));
				} else {
					return null;
				}
			}
			TL_error result = new TL_error();
			result.readParams(stream, exception);
			return result;
		}

		public void readParams(AbstractSerializedData stream, boolean exception) {
            code = stream.readInt32(exception);
			text = stream.readString(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt32(code);
			stream.writeString(text);
		}
	}
	
	public static class InputFileLocation extends TLObject {
		public long id;
		public long access_hash;
		public long volume_id;
		public int local_id;
		public long secret;

		public static InputFileLocation TLdeserialize(AbstractSerializedData stream, int constructor, boolean exception) {
			InputFileLocation result = null;
			switch(constructor) {
				case 0xf5235d55:
					result = new TL_inputEncryptedFileLocation();
					break;
				case 0x4e45abe9:
					result = new TL_inputDocumentFileLocation();
					break;
				case 0x14637196:
					result = new TL_inputFileLocation();
					break;
			}
			if (result == null && exception) {
				throw new RuntimeException(String.format("can't parse magic %x in InputFileLocation", constructor));
			}
			if (result != null) {
				result.readParams(stream, exception);
			}
			return result;
		}
	}

	public static class TL_inputEncryptedFileLocation extends InputFileLocation {
		public static int constructor = 0xf5235d55;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			access_hash = stream.readInt64(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt64(access_hash);
		}
	}

	public static class TL_inputDocumentFileLocation extends InputFileLocation {
        public static int constructor = 0x4e45abe9;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			id = stream.readInt64(exception);
			access_hash = stream.readInt64(exception);
		}

		public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(id);
			stream.writeInt64(access_hash);
		}
	}

	public static class TL_inputFileLocation extends InputFileLocation {
		public static int constructor = 0x14637196;


		public void readParams(AbstractSerializedData stream, boolean exception) {
			volume_id = stream.readInt64(exception);
			local_id = stream.readInt32(exception);
			secret = stream.readInt64(exception);
		}

        public void serializeToStream(AbstractSerializedData stream) {
			stream.writeInt32(constructor);
			stream.writeInt64(volume_id);
			stream.writeInt32(local_id);
			stream.writeInt64(secret);
		}
	}
	
	

}
